package com.helltar.fosshine.link

import android.os.SystemClock
import android.util.Log
import com.helltar.fosshine.protocol.Frame
import com.helltar.fosshine.protocol.FrameReader
import com.helltar.fosshine.protocol.Telemetry
import com.helltar.fosshine.protocol.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Plays the phone's part of the Fossibot+ offline mode: the station joins nothing, it runs its own AP and
 * connects out to whoever is on it, port 8058. This listens, keeps the latest station connection, answers
 * every frame with the app's fixed ack and publishes each telemetry frame.
 */
class StationServer(private val scope: CoroutineScope, private val isTrusted: (InetAddress) -> Boolean) {

    sealed interface State {
        data object Stopped : State
        data object Listening : State
        data class Connected(val address: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    /** When the last telemetry frame arrived, in [SystemClock.elapsedRealtime], or 0 before the first one. */
    private val _lastFrameAt = MutableStateFlow(0L)
    val lastFrameAt: StateFlow<Long> = _lastFrameAt.asStateFlow()

    private val writeLock = Mutex()
    private var serverSocket: ServerSocket? = null
    private var station: Socket? = null
    private var acceptJob: Job? = null

    fun start() {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch { acceptLoop() }
    }

    fun stop() {
        acceptJob?.cancel()
        closeQuietly(serverSocket)
        closeQuietly(station)
        serverSocket = null
        station = null
        _state.value = State.Stopped
    }

    /**
     * Writes one register and waits for telemetry to show the new value, resending when it does not:
     * the station gives no other reply, and it does drop a write now and then.
     */
    suspend fun write(key: Int, value: Int): Boolean = writeLock.withLock {
        repeat(WRITE_ATTEMPTS) { attempt ->
            if (!send(Frame.write(key, value))) return false

            val confirmed = withTimeoutOrNull(WRITE_CONFIRM_TIMEOUT) {
                telemetry.drop(1).filterNotNull().first { it.raw(key) == value }
            }
            if (confirmed != null) return true

            Log.w(TAG, "write $key=$value not reflected, attempt ${attempt + 1}")
        }
        false
    }

    private suspend fun acceptLoop() = withContext(Dispatchers.IO) {
        // this loop's own job, not the scope's: a stop and start in quick succession leaves two loops for a moment
        while (isActive) {
            try {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(PORT))
                    serverSocket = server
                    _state.value = State.Listening

                    while (isActive) {
                        val socket = server.accept()

                        // the port is open on every interface; only the station's own AP may reach the station slot,
                        // so a host on the home network can neither feed fake telemetry nor push the station out
                        if (!isTrusted(socket.inetAddress)) {
                            Log.w(TAG, "refused ${socket.inetAddress.hostAddress}: not on the station's network")
                            closeQuietly(socket)
                            continue
                        }

                        closeQuietly(station)
                        station = socket
                        scope.launch(Dispatchers.IO) { serve(socket) }
                    }
                }
            } catch (e: IOException) {
                if (!isActive) return@withContext
                Log.w(TAG, "server failed", e)
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
                delay(RETRY_DELAY)
            }
        }
    }

    private fun serve(socket: Socket) {
        val address = socket.inetAddress.hostAddress ?: "?"
        _state.value = State.Connected(address)
        val reader = FrameReader()
        val buffer = ByteArray(4096)

        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break

                for (bytes in reader.feed(buffer.copyOf(count))) {
                    val frame = Frame.parse(bytes)
                    if (frame == null) {
                        Log.w(TAG, "bad frame ${bytes.toHex()}")
                        continue
                    }

                    synchronized(this) { output.write(Frame.ACK) }

                    if (frame.kind == Frame.KIND_TELEMETRY) {
                        _telemetry.value = Telemetry(frame.registers)
                        _lastFrameAt.value = SystemClock.elapsedRealtime()
                    }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "station $address gone: ${e.message}")
        } finally {
            closeQuietly(socket)
            if (station === socket) {
                station = null
                if (serverSocket != null) _state.value = State.Listening
            }
        }
    }

    private suspend fun send(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val socket = station ?: return@withContext false
        try {
            synchronized(this@StationServer) { socket.getOutputStream().write(bytes) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "send failed", e)
            false
        }
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val PORT = 8058

        private const val TAG = "StationServer"
        private const val WRITE_ATTEMPTS = 3
        private val WRITE_CONFIRM_TIMEOUT = 2_500.milliseconds
        private const val READ_TIMEOUT_MS = 15_000
        private val RETRY_DELAY = 3.seconds
    }
}
