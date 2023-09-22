use eyre::Result;
use jni::{
    objects::{JByteArray, JObject, JString},
    sys::{jbyteArray, jlong, jstring},
    JNIEnv,
};

use crate::KassandraBridge;

fn unwrap_or_throw<T>(env: &mut JNIEnv, result: Result<T>, sentinel: T) -> T {
    match result {
        Ok(r) => r,
        Err(e) => {
            match env.exception_occurred() {
                Ok(throwable) if throwable.is_null() => {
                    env.throw_new("java/lang/RuntimeException", &format!("{}", e))
                        .expect("failed to throw java exception");
                }
                Ok(_throwable) => {}
                Err(_) => {
                    env.throw_new("java/lang/RuntimeException", &format!("{}", e))
                        .expect("failed to throw java exception");
                }
            }
            sentinel
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_initialize<'a>(
    _env: JNIEnv<'a>,
    _class: JObject<'a>,
) -> jlong {
    let data = Box::new(KassandraBridge::empty());

    Box::into_raw(data) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_initializeFromState(
    mut env: JNIEnv,
    _class: JObject,
    state: JString,
) -> jlong {
    let res = helpers::jstring_to_string(&mut env, &state)
        .and_then(|state| Ok(Box::new(KassandraBridge::from_state(&state)?)))
        .map(|it| Box::into_raw(it) as _);

    unwrap_or_throw(&mut env, res, 0)
}

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_state(
    mut env: JNIEnv,
    _class: JObject,
    ptr: jlong,
) -> jstring {
    let bridge: Box<KassandraBridge> = unsafe { Box::from_raw(ptr as _) };
    let res = save_state(&mut env, &bridge);
    let _ = Box::leak(bridge);

    let sentinel = env.new_string("").unwrap().into_raw();
    unwrap_or_throw(&mut env, res, sentinel)
}

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_snapshot(
    env: JNIEnv,
    _class: JObject,
    ptr: jlong,
) -> jstring {
    let bridge: Box<KassandraBridge> = unsafe { Box::from_raw(ptr as _) };

    let data = bridge.snapshot();
    let _ = Box::leak(bridge);

    env.new_string(data).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_process<'a>(
    mut env: JNIEnv<'a>,
    _class: JObject<'a>,
    ptr: jlong,
    header: JByteArray<'a>,
    payload: JByteArray<'a>,
) -> jbyteArray {
    let bridge: Box<KassandraBridge> = unsafe { Box::from_raw(ptr as _) };
    let res = process(&mut env, &bridge, header, payload);
    let sent = env.new_byte_array(0).unwrap().into_raw();
    let _ = Box::leak(bridge);
    unwrap_or_throw(&mut env, res, sent)
}

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_finalize<'a>(
    _env: JNIEnv<'a>,
    _class: JObject<'a>,
    ptr: jlong,
) {
    let _: Box<KassandraBridge> = unsafe { Box::from_raw(ptr as _) };
}

fn save_state(env: &mut JNIEnv, bridge: &KassandraBridge) -> Result<jstring> {
    let state = bridge.save_state()?;
    Ok(helpers::string_to_jstring(env, state)?)
}

fn process(
    env: &mut JNIEnv,
    bridge: &KassandraBridge,
    header: JByteArray,
    payload: JByteArray,
) -> Result<jbyteArray> {
    let header = helpers::array_to_bytes(env, header)?;
    let payload = helpers::array_to_bytes(env, payload)?;

    let response = bridge.process(header, payload)?;

    Ok(helpers::bytes_to_array(env, response)?)
}

mod helpers {
    use std::mem::transmute;

    use eyre::{Result, WrapErr};
    use jni::{
        objects::{JByteArray, JString},
        sys::{jbyteArray, jstring},
        JNIEnv,
    };

    pub fn jstring_to_string(env: &mut JNIEnv, string: &JString) -> Result<String> {
        Ok(env
            .get_string(string)
            .wrap_err("Could not read string")?
            .into())
    }

    pub fn string_to_jstring(env: &mut JNIEnv, string: String) -> Result<jstring> {
        Ok(env
            .new_string(string)
            .wrap_err("Could not create string")?
            .into_raw())
    }

    pub fn array_to_bytes(env: &JNIEnv, array: JByteArray) -> Result<Vec<u8>> {
        let size = env
            .get_array_length(&array)
            .wrap_err("Could not get size of array")? as _;
        if size == 0 {
            return Ok(vec![]);
        }
        let mut output = vec![0i8; size];

        env.get_byte_array_region(&array, 0, output.as_mut_slice())
            .wrap_err("Could not read array content")?;

        Ok(unsafe { transmute(output) })
    }

    pub fn bytes_to_array(env: &JNIEnv, bytes: Vec<u8>) -> Result<jbyteArray> {
        let output = env
            .new_byte_array(bytes.len() as _)
            .wrap_err("Could not create array")?;
        let bytes: Vec<i8> = unsafe { transmute(bytes) };
        env.set_byte_array_region(&output, 0, bytes.as_slice())
            .wrap_err("Could not copy bytes to array")?;

        Ok(output.into_raw())
    }
}
