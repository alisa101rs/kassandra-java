package com.github.kassandra


public object Kassandra {
    private external fun initialize(): Long
    private external fun initializeFromState(state: String): Long
    private external fun state(ptr: Long): String
    private external fun snapshot(ptr: Long): String
    private external fun process(ptr: Long, header: ByteArray, payload: ByteArray): ByteArray
    private external fun finalize(ptr: Long)

    init {
        NativeLoader.init()
    }

    public fun create(): KassandraSession = KassandraSession(initialize())
    public fun createWithState(state: String): KassandraSession = KassandraSession(initializeFromState(state))
    internal fun saveState(session: KassandraSession): String = state(session.ptr)
    internal fun snapshot(session: KassandraSession): String = snapshot(session.ptr)
    internal fun process(session: KassandraSession, header: ByteArray, payload: ByteArray): ByteArray = process(session.ptr, header, payload)
    internal fun finalize(session: KassandraSession): Unit = finalize(session.ptr)
}

