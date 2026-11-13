package com.helltar.fosshine.link

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.helltar.fosshine.R
import com.helltar.fosshine.protocol.Writable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one connection to the station for the whole app, so the screen and [com.helltar.fosshine.StationService] share it
 * and it outlives the activity. The service starts and stops it; the screen only reads it and asks for writes.
 */
class StationLink private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val hotspot = HotspotConnector(context)
    val server = StationServer(scope, hotspot::isOnStationNetwork)
    val beacon = StationBeacon(context, scope) { hotspot.serial.value }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Register keys with a write in flight, so the UI can show them as busy. */
    private val _pending = MutableStateFlow<Set<Int>>(emptySet())
    val pending: StateFlow<Set<Int>> = _pending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** A join asked for while the station was starting up, held until it is ready. */
    private val _joinHeld = MutableStateFlow(false)
    val joinHeld: StateFlow<Boolean> = _joinHeld.asStateFlow()

    init {
        // HotspotConnector's callbacks and requests run on the main thread, so the held join does too
        scope.launch(Dispatchers.Main) {
            beacon.phase.collect { phase ->
                if (phase == StationBeacon.Phase.Starting) holdIfJoining()
                // ready, or no longer listened for (the app went to the background): join now rather than never
                else if (_joinHeld.value) {
                    _joinHeld.value = false
                    hotspot.rejoin()
                }
            }
        }
    }

    fun start() {
        server.start()
        _running.value = true
    }

    fun stop() {
        _joinHeld.value = false
        hotspot.forget()
        server.stop()
        _running.value = false
    }

    /**
     * Joins the station's AP, or, while it is still starting up, waits until it is ready: a phone joined during
     * the setup phase gets dropped when the station enters offline mode, and if it is not back on the AP soon
     * enough the station falls back to setup for another round of a minute or two.
     */
    fun join() {
        if (beacon.phase.value == StationBeacon.Phase.Starting) {
            hotspot.release()
            _joinHeld.value = true
        } else {
            hotspot.request()
        }
    }

    /**
     * Steps off the AP when the station turns out to be starting up while the phone is on it or rejoining it,
     * as after the station was switched off and on. Only for a known AP, which the rejoin finds without the
     * picker: a picker the person is looking at stays.
     */
    private fun holdIfJoining() {
        val joining = hotspot.state.value.let {
            it == HotspotConnector.State.Requesting || it == HotspotConnector.State.Connected || it == HotspotConnector.State.Lost
        }
        if (_running.value && joining && hotspot.knowsStation && server.state.value !is StationServer.State.Connected) {
            hotspot.release()
            _joinHeld.value = true
        }
    }

    fun write(key: Int, value: Int) {
        if (key in _pending.value) return

        // the controls offer nothing else; this holds even if one of them is wrong
        if (!Writable.allows(key, value, hotspot.serial.value)) {
            Log.e(TAG, "refused write $key=$value: not a value the vendor's settings screen writes")
            _error.value = context.getString(R.string.write_failed)
            return
        }

        // the Fossibot+ app sends nothing while the battery reads 0 %, so neither does this
        if (server.telemetry.value?.soc == 0) {
            _error.value = context.getString(R.string.battery_empty)
            return
        }

        _pending.update { it + key }

        scope.launch {
            val ok = server.write(key, value)
            _pending.update { it - key }
            if (!ok) _error.value = context.getString(R.string.write_failed)
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        private const val TAG = "StationLink"

        // holds only the application context, which lives as long as the process
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: StationLink? = null

        fun get(context: Context): StationLink =
            instance ?: synchronized(this) {
                instance ?: StationLink(context.applicationContext).also { instance = it }
            }
    }
}
