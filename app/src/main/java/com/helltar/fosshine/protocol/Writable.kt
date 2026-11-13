package com.helltar.fosshine.protocol

/**
 * The registers Fosshine writes and the values each may take: what the Fossibot+ settings screen writes,
 * within its ranges, and nothing else. The firmware may not validate a write, so this does, in one place,
 * for every write the app sends; the controls take their ranges from here too.
 */
object Writable {

    const val CHARGE_POWER_MIN = 100
    const val CHARGE_POWER_STEP = 100

    val LED_MODE = 0..3
    val CHARGE_UPPER_LIMIT = 60..100
    val DISCHARGE_LOWER_LIMIT = 0..20
    val DC_CHARGE_CURRENT = 1..8
    val SCREEN_BRIGHTNESS = 0..100

    private val SWITCH = 0..1

    /** The charge power range in watts; its top depends on the model the serial names. */
    fun chargePower(serial: String?): IntRange = CHARGE_POWER_MIN..Model.chargePowerMax(serial)

    /** Whether the DC charging current can be set: Fossibot+ 1.0.26 takes that setting away from the F3000. */
    fun hasDcChargeCurrent(serial: String?): Boolean = !Model.isF3000(serial)

    /** Whether [value] is one the settings screen would write to register [key] on the station with this serial. */
    fun allows(key: Int, value: Int, serial: String?): Boolean = when (key) {
        Reg.AC_SWITCH, Reg.DC_SWITCH, Reg.USB_SWITCH, Reg.ECO_MODE, Reg.KEY_SOUND_OFF, Reg.OUTPUT_MEMORY -> value in SWITCH
        Reg.LED_MODE -> value in LED_MODE
        Reg.CHARGE_POWER_LIMIT -> value in chargePower(serial) && value % CHARGE_POWER_STEP == 0
        Reg.CHARGE_UPPER_LIMIT -> value in CHARGE_UPPER_LIMIT
        Reg.DISCHARGE_LOWER_LIMIT -> value in DISCHARGE_LOWER_LIMIT
        Reg.DC_CHARGE_CURRENT -> hasDcChargeCurrent(serial) && value in DC_CHARGE_CURRENT
        Reg.SCREEN_BRIGHTNESS -> value in SCREEN_BRIGHTNESS
        Reg.SCREEN_OFF_TIME -> value in Options.SCREEN_OFF_TIME.indices
        Reg.SHUTDOWN_TIME -> value in Options.SHUTDOWN_TIME.indices
        Reg.AC_IDLE_OFF, Reg.DC_IDLE_OFF, Reg.USB_IDLE_OFF -> value in Options.IDLE_OFF.indices
        else -> false
    }
}
