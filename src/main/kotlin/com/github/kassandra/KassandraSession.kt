package com.github.kassandra


import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.toJavaAddress
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.core.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean


public class KassandraSession internal constructor(internal val ptr: Long) : Closeable {
    private val job = SupervisorJob()
    private val socket: ServerSocket = aSocket(ActorSelectorManager(job))
        .tcp()
        .bind(hostname = "localhost", port = 0)
    private val started: AtomicBoolean = AtomicBoolean(false)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    public val address: InetSocketAddress
        get() = InetSocketAddress(
            socket.localAddress.toJavaAddress().hostname,
            socket.localAddress.toJavaAddress().port
        )

    public fun start() {
        if (started.get()) return
        started.set(true)
        CoroutineScope(job).listen()
    }

    public fun rawSnapshot(): String {
        require(!closed.get()) { "Can't save snapshot of closed session" }
        return Kassandra.snapshot(this)
    }

    public fun snapshot(): DataSnapshot =
        Json.decodeFromString(rawSnapshot())

    public fun saveState(): String {
        require(!closed.get()) { "Can't save state of closed session" }
        return Kassandra.saveState(this)
    }

    private fun process(header: ByteArray, payload: ByteArray): ByteArray {
        require(!closed.get()) { "Session is already closed" }
        return Kassandra.process(this, header, payload)
    }

    private fun CoroutineScope.listen() {
        launch(Dispatchers.IO) {
            while (isActive) {
                val client = socket.accept()
                serve(client)
            }
        }
    }

    private fun CoroutineScope.serve(client: Socket) = launch {
        val read = client.openReadChannel()
        val output = client.openWriteChannel(false)

        while (isActive) {
            val (header, payload) = read.readRequest()
            val response = process(header, payload)

            ByteReadChannel(response).copyTo(output)
            output.flush()
        }
    }

    private suspend fun ByteReadChannel.readRequest(): Pair<ByteArray, ByteArray> {
        while (true) {
            if (availableForRead >= 9) break
            awaitContent()
        }

        val headerPacket = readPacket(9)
        val header = ByteArray(9)
        headerPacket.readAvailable(header, 0, 9)


        val length = ByteReadChannel(header).let {
            it.readByte() // version
            it.readByte() // flags
            it.readShort() // stream
            it.readByte() // opcode
            val length = it.readInt()

            length
        }


        while (true) {
            if (availableForRead >= length) break
            awaitContent()
        }

        val payload = ByteArray(length)
        readAvailable(payload, 0, length)

        return header to payload
    }

    override fun close() {
        if (!job.isCancelled) {
            job.cancel(CancellationException("stopped"))
        }

        if (closed.get()) return
        closed.set(true)
        Kassandra.finalize(this)
    }
}


