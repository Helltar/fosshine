package com.helltar.fosshine.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import androidx.annotation.RequiresApi
import com.helltar.fosshine.protocol.Model
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Asks Android to join the station's own AP. The system shows its network picker, and the connection is
 * local-only: on phones that support it, the regular Wi-Fi stays up beside it. The network lasts as long as
 * the request, so [release] drops it.
 */
class HotspotConnector(context: Context) {

    enum class State { Idle, Requesting, Connected, Unavailable, Lost }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * The serial in the joined AP's name. Android hands the name over only with the location permission
     * (and from Android 12 only to a callback that asks for it), so without one this stays null.
     */
    private val _serial = MutableStateFlow<String?>(null)
    val serial: StateFlow<String?> = _serial.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    private val main = Handler(Looper.getMainLooper())

    /** The addresses this phone holds on the station's AP while connected; the station is only trusted from them. */
    @Volatile
    private var links: List<LinkAddress> = emptyList()

    /**
     * Whether [peer] sits on the station's AP subnet, the only place the station can connect from. IPv4 only:
     * the station speaks nothing else, and the AP's IPv6 link-local prefix is the same fe80::/64 every other
     * interface has, so it would let the home network in.
     */
    fun isOnStationNetwork(peer: InetAddress): Boolean =
        peer is Inet4Address && links.any { it.address is Inet4Address && it.covers(peer) }

    /**
     * The AP joined last once Android has named it, kept until [forget]: a request for exactly that AP can be
     * approved without the network picker, where a request by prefix always shows it.
     */
    @Volatile
    private var joined: Pair<String, MacAddress>? = null

    /** Whether [rejoin] can ask for one known AP rather than show the picker. */
    val knowsStation: Boolean get() = joined != null

    /** Joins any station's AP through the system's network picker. */
    fun request() = request(null)

    /** Joins the AP joined last, or any station's through the picker when there is none. */
    fun rejoin() = request(joined)

    private fun request(ap: Pair<String, MacAddress>?) {
        release()

        val builder = WifiNetworkSpecifier.Builder().setWpa2Passphrase(PASSPHRASE)
        ap?.let { (ssid, bssid) -> builder.setSsid(ssid).setBssid(bssid) }
            ?: builder.setSsidPattern(PatternMatcher(SSID_PREFIX, PatternMatcher.PATTERN_PREFIX))
        val specifier = builder.build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        // the flag that lets the callback see the AP's name exists from Android 12, and so does the constructor
        val networkCallback =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
            else Callback()

        callback = networkCallback
        _state.value = State.Requesting
        connectivity.requestNetwork(request, networkCallback)
    }

    /** Leaves the AP, still knowing which one it was, for a [rejoin]. */
    fun release() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        links = emptyList()
        _state.value = State.Idle
    }

    /** Leaves the AP and forgets it, so the next join goes through the picker. */
    fun forget() {
        release()
        joined = null
        _serial.value = null
    }

    private inner class Callback : ConnectivityManager.NetworkCallback {

        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onAvailable(network: Network) {
            links = connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
            _state.value = State.Connected
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            links = properties.linkAddresses
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val info = capabilities.transportInfo as? WifiInfo ?: return
            val serial = Model.serialOf(info.ssid, SSID_PREFIX) ?: return
            _serial.value = serial

            // without the location permission Android hides the BSSID behind this placeholder
            info.bssid?.takeIf { it != HIDDEN_BSSID }?.let {
                joined = SSID_PREFIX + serial to MacAddress.fromString(it)
            }
        }

        override fun onUnavailable() {
            links = emptyList()
            _state.value = State.Unavailable
        }

        override fun onLost(network: Network) {
            links = emptyList()
            _state.value = State.Lost

            // the station drops its client when it leaves the setup phase for offline mode, the moment it is
            // about to dial, so join again; off the callback's thread, since rejoin() unregisters this callback
            main.post { if (callback === this) rejoin() }
        }
    }

    // IpPrefix.contains needs API 30, one above minSdk
    private fun LinkAddress.covers(peer: InetAddress): Boolean {
        val own = address.address
        val other = peer.address
        if (own.size != other.size) return false

        var bits = prefixLength
        for (i in own.indices) {
            if (bits <= 0) return true
            val mask = if (bits >= 8) 0xff else (0xff shl (8 - bits)) and 0xff
            if ((own[i].toInt() and mask) != (other[i].toInt() and mask)) return false
            bits -= 8
        }
        return true
    }

    companion object {
        // the Fossibot+ app hard-codes both: every station's AP is FOSS_<serial> with this password
        const val SSID_PREFIX = "FOSS_"
        const val PASSPHRASE = "FOSSIBOT"

        private const val HIDDEN_BSSID = "02:00:00:00:00:00"
    }
}
