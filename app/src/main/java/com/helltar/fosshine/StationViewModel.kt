package com.helltar.fosshine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.helltar.fosshine.link.StationLink
import com.helltar.fosshine.protocol.Reg

/** The screen's view of [StationLink]; the link itself lives in [StationService], not here. */
class StationViewModel(application: Application) : AndroidViewModel(application) {

    private val link = StationLink.get(application)

    val server = link.server
    val hotspot = link.hotspot
    val beacon = link.beacon
    val joinHeld = link.joinHeld
    val running = link.running
    val pending = link.pending
    val error = link.error

    /** Starts the service (if it is not running yet) and joins the station's AP, once it is ready to be joined. */
    fun connect() {
        StationService.start(getApplication())
        // the permission may have been granted just now, after the connect card tried to listen without it
        link.beacon.start()
        link.join()
    }

    /** Listens for the station's setup phase while the connect card is on screen. */
    fun watchBeacon() = link.beacon.start()

    fun stopBeacon() = link.beacon.stop()

    fun disconnect() = StationService.stop(getApplication())

    fun write(key: Int, value: Int) = link.write(key, value)

    fun setKeySound(on: Boolean) = write(Reg.KEY_SOUND_OFF, if (on) 0 else 1)

    fun clearError() = link.clearError()
}
