package com.helltar.fosshine.protocol

/**
 * What the Fossibot+ app tells from a station's serial, the only model hint it has: the first four characters,
 * `F180` for the F1800 and `F300` for the F3000. Without a serial it assumes an F1800, and so does this.
 */
object Model {

    private const val F1800 = "F180"
    private const val F3000 = "F300"

    /** The serial in an AP name like `FOSS_F180V012345A0001`, or null for any other name. */
    fun serialOf(ssid: String?, prefix: String): String? =
        ssid?.removeSurrounding("\"")?.takeIf { it.startsWith(prefix) && it.length > prefix.length }?.removePrefix(prefix)

    fun isF1800(serial: String?): Boolean = serial?.take(4)?.uppercase() == F1800

    fun isF3000(serial: String?): Boolean = serial?.take(4)?.uppercase() == F3000

    /** The top of the charge power range (register 42) on the app's settings screen. */
    fun chargePowerMax(serial: String?): Int = if (isF3000(serial)) 2000 else 1200
}
