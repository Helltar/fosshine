package com.helltar.fosshine.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Tells whether the station is still starting up. After power-on it spends a minute or more in a setup phase
 * (its Wi-Fi icon blinks), advertising over BLE while it waits to be provisioned, and only dials the phone once
 * it has moved on to offline mode (the Bluetooth icon blinks), when the advertising stops. This only listens:
 * it never connects to the station.
 */
class StationBeacon(context: Context, private val scope: CoroutineScope, private val serial: () -> String?) {

    enum class Phase {
        /** Nothing heard: no permission, Bluetooth off, or the station was past its setup phase already. */
        Unknown,

        /** Advertising: the station is in its setup phase and will not dial yet. */
        Starting,

        /** Advertising heard and then stopped: offline mode has started, the station is about to dial. */
        Ready
    }

    private val context = context.applicationContext
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _phase = MutableStateFlow(Phase.Unknown)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** When this round of the setup phase was first heard, in [SystemClock.elapsedRealtime], or null outside it. */
    private val _startingSince = MutableStateFlow<Long?>(null)
    val startingSince: StateFlow<Long?> = _startingSince.asStateFlow()

    @Volatile
    private var lastHeardAt = 0L

    /** [startingSince] as it was before a [stop], for a [start] that picks the same round up again. */
    private var roundSince = 0L

    private var scanner: BluetoothLeScanner? = null
    private var ticker: Job? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // the advertised name, not BluetoothDevice.getName(), which needs BLUETOOTH_CONNECT
            val name = result.scanRecord?.deviceName ?: return
            val own = serial()?.let { HotspotConnector.SSID_PREFIX + it }
            if (own?.let { name == it } ?: name.startsWith(HotspotConnector.SSID_PREFIX)) {
                val now = SystemClock.elapsedRealtime()
                // the gaps say how much silence is still the setup phase, which SILENT_AFTER_MS has to outlast
                if (lastHeardAt != 0L && now - lastHeardAt >= 1_000) Log.d(TAG, "heard after ${now - lastHeardAt} ms of silence")
                lastHeardAt = now
                if (_phase.value != Phase.Starting) {
                    Log.d(TAG, "setup phase heard")
                    roundSince = now
                    _startingSince.value = now
                }
                _phase.value = Phase.Starting
            }
        }
    }

    @SuppressLint("MissingPermission") // canScan checks it
    fun start() {
        if (scanner != null || !canScan()) return
        val leScanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return

        // no ScanFilter: one cannot match a name prefix, and the serial is known only once the AP is joined
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        if (runCatching { leScanner.startScan(null, settings, callback) }.isFailure) return

        scanner = leScanner

        // a stop this short (another app's dialog, a glance at the recents) is not the station going quiet: the
        // round heard before it goes on, where forgetting it would restart the count and let a join through
        if (lastHeardAt != 0L && SystemClock.elapsedRealtime() - lastHeardAt <= SILENT_AFTER_MS) {
            _startingSince.value = roundSince
            _phase.value = Phase.Starting
        } else {
            lastHeardAt = 0L
            _startingSince.value = null
            _phase.value = Phase.Unknown
        }

        ticker = scope.launch {
            while (isActive) {
                delay(TICK)
                if (_phase.value == Phase.Starting && SystemClock.elapsedRealtime() - lastHeardAt > SILENT_AFTER_MS) {
                    Log.d(TAG, "silent for ${SystemClock.elapsedRealtime() - lastHeardAt} ms: ready")
                    _phase.value = Phase.Ready
                    _startingSince.value = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // a scan only runs once canScan allowed it
    fun stop() {
        ticker?.cancel()
        ticker = null
        scanner?.let { runCatching { it.stopScan(callback) } }
        scanner = null
        _startingSince.value = null
        _phase.value = Phase.Unknown
    }

    private fun canScan(): Boolean {
        // up to Android 11 a BLE scan needs the location permission the app asks for anyway
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
            else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "StationBeacon"

        val TICK = 1.seconds

        // a pixel 9 heard the advertising more than once a second through a whole setup phase, with one gap of
        // 5 s at its end, so a silence of this long means it stopped
        const val SILENT_AFTER_MS = 12_000L
    }
}
