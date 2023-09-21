package com.github.kassandra



public object Kassandra {
    private external fun callRustCode(): String

    init {
        NativeLoader.loadLibrary(
            javaClass.classLoader,
            System.mapLibraryName("kassandra_jni"),
        )
    }

    public fun hello() {
        println(callRustCode())
    }
}
