use std::sync::{Arc, Mutex};

use base64::Engine;
use bytes::{Buf, BufMut, BytesMut};
use eyre::{eyre, Context, ContextCompat, Result};
use kassandra::{
    frame::{
        request::{Request, RequestOpcode},
        response::Response,
        FrameFlags, FrameParams,
    },
    KassandraSession,
};

pub mod bindings;

#[derive(Clone)]
struct KassandraBridge {
    kassandra: Arc<Mutex<KassandraSession>>,
}

impl KassandraBridge {
    pub fn empty() -> Self {
        Self::new(KassandraSession::new())
    }
    pub fn new(kassandra: KassandraSession) -> Self {
        Self {
            kassandra: Arc::new(Mutex::new(kassandra)),
        }
    }

    pub fn from_state(state: &str) -> Result<Self> {
        use std::io::Read;

        let state = base64::prelude::BASE64_STANDARD
            .decode(state)
            .wrap_err("Invalid base64 state")?;
        let mut decoder =
            libflate::gzip::Decoder::new(state.as_slice()).wrap_err("Could not read gzip")?;
        let mut state = Vec::new();
        let _ = decoder
            .read_to_end(&mut state)
            .wrap_err("Could not read gzip")?;

        let kassandra =
            KassandraSession::load_state(&state).wrap_err("Could not deserialize state")?;
        Ok(Self::new(kassandra))
    }

    pub fn save_state(&self) -> Result<String> {
        use std::io::Write;

        let state = {
            let kassandra = self.kassandra.lock().map_err(|_| eyre!("mutex poisoned"))?;

            kassandra.save_state()
        };

        let mut encoder = libflate::gzip::Encoder::new(Vec::new()).wrap_err("gzip error")?;
        let _ = encoder.write_all(&state).wrap_err("gzip error")?;
        let state = encoder
            .finish()
            .into_result()
            .wrap_err("Could not compress with gzip")?;
        Ok(base64::prelude::BASE64_STANDARD.encode(&state))
    }

    pub fn snapshot(&self) -> String {
        let kassandra = self.kassandra.lock().unwrap();
        let snapshot = kassandra.data_snapshot();
        serde_json::to_string(&snapshot).unwrap()
    }

    pub fn process(&self, header: Vec<u8>, payload: Vec<u8>) -> Result<Vec<u8>> {
        let (request_frame, request) = deserialize_request(&header, &payload)?;

        let response = match request {
            Request::StartUp(_options) => Response::Ready,
            Request::Options => Response::options(),
            Request::Query(query) => {
                let mut kass = self.kassandra.lock().map_err(|_| eyre!("mutex poisoned"))?;
                match kass.process(query) {
                    Ok(res) => Response::Result(res),
                    Err(er) => Response::Error(er),
                }
            }
            Request::Prepare(prep) => {
                let mut kass = self.kassandra.lock().map_err(|_| eyre!("mutex poisoned"))?;
                match kass.prepare(prep) {
                    Ok(res) => Response::Result(res),
                    Err(er) => Response::Error(er),
                }
            }
            Request::Execute(execute) => {
                let mut kass = self.kassandra.lock().map_err(|_| eyre!("mutex poisoned"))?;
                match kass.execute(execute) {
                    Ok(res) => Response::Result(res),
                    Err(er) => Response::Error(er),
                }
            }
            Request::Register { events: _ } => Response::Ready,
            Request::Batch(b) => {
                let mut kass = self.kassandra.lock().map_err(|_| eyre!("mutex poisoned"))?;
                match kass.process_batch(b) {
                    Ok(res) => Response::Result(res),
                    Err(er) => Response::Error(er),
                }
            }
            Request::AuthResponse => Response::Ready,
        };

        Ok(serialize_response(response, request_frame.stream)?)
    }
}

fn serialize_response(response: Response, stream: i16) -> Result<Vec<u8>> {
    let mut flags = FrameFlags::empty();
    let mut output = BytesMut::new();

    output.resize(9, 0);
    response
        .serialize(&mut output, &mut flags)
        .wrap_err("failed to serialized response")?;

    {
        let (mut header, data) = output.split_at_mut(9);
        header.put_u8(0x84); // version
        header.put_u8(flags.bits());
        header.put_i16(stream);
        header.put_u8(response.opcode());
        header.put_u32(data.len() as _);
    }

    Ok(output.to_vec())
}

fn deserialize_request<'a>(
    mut header: &[u8],
    payload: &'a [u8],
) -> Result<(FrameParams, Request<'a>)> {
    if header.len() != 9 {
        return Err(eyre!("Header size is of invalid length"));
    }

    let frame = FrameParams {
        version: header.get_u8(),
        flags: FrameFlags::from_bits(header.get_u8()).wrap_err("Invalid frame flags")?,
        stream: header.get_i16(),
    };

    let opcode = RequestOpcode::try_from(header.get_u8()).wrap_err("Invalid request opcode")?;
    let size = header.get_u32();

    if size as usize != payload.len() {
        return Err(eyre!("Payload is of invalid length"));
    }

    let request =
        Request::deserialize(opcode, payload).wrap_err("Could not deserialize request frame")?;
    Ok((frame, request))
}
