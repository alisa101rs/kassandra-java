use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString};
use jni::sys::jstring;

#[no_mangle]
pub extern "system" fn Java_com_github_kassandra_Kassandra_callRustCode<'local>(
    env: JNIEnv<'local>,
    _class: JObject<'local>,
    //_input: JString<'local>
) -> jstring {
    env.new_string("Hello from rust!").unwrap().into_raw()
}