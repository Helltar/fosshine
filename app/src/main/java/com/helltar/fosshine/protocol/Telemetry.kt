package com.helltar.fosshine.protocol

/** Register keys, from the Fossibot+ app's register table: the ones Fosshine reads or writes. */
object Reg {
    const val SOC = 1
    const val BATTERY_TEMP_MAX = 2
    const val REMAINING_TIME = 3
    const val CHARGING = 4
    const val BMS_VERSION = 5
    const val PCS_VERSION = 18
    const val AC_INPUT_POWER = 19
    const val AC_OUTPUT_POWER = 20
    const val AC_OUTPUT_VOLTAGE = 21
    const val AC_OUTPUT_FREQUENCY = 22
    const val PV_CHARGE_POWER = 23
    const val INVERTER_TEMP = 24
    const val TOTAL_INPUT_POWER = 34
    const val TOTAL_OUTPUT_POWER = 35
    const val DC_POWER = 36
    const val USB_POWER = 37
    const val LED_MODE = 38
    const val AC_SWITCH = 39
    const val DC_SWITCH = 40
    const val USB_SWITCH = 41
    const val CHARGE_POWER_LIMIT = 42
    const val OUTPUT_MEMORY = 43
    const val SCREEN_OFF_TIME = 44
    const val SHUTDOWN_TIME = 45
    const val ECO_MODE = 46
    const val FIRMWARE_VERSION = 47
    const val PV_ENERGY = 48
    const val CHARGE_UPPER_LIMIT = 49
    const val DISCHARGE_LOWER_LIMIT = 50
    const val KEY_SOUND_OFF = 51
    const val SCREEN_BRIGHTNESS = 52
    const val DC_CHARGE_CURRENT = 53
    const val SCHEDULED_CHARGE = 54
    const val SCHEDULED_DISCHARGE = 55
    const val DC_IDLE_OFF = 56
    const val USB_IDLE_OFF = 57
    const val AC_IDLE_OFF = 58

    /** The registers written as 16 bits; every other writable one is a single byte. */
    val WIDE = setOf(CHARGE_POWER_LIMIT)
}

/** One telemetry frame's registers, with typed reads for the ones the app shows. */
class Telemetry(private val registers: Map<Int, ByteArray>) {

    val soc: Int? get() = u8(Reg.SOC)
    val batteryTempMax: Int? get() = registers[Reg.BATTERY_TEMP_MAX]?.let { it[0].toInt() }
    /** The station's estimate; it never goes past [REMAINING_MAX], so that value means "at least that long". */
    val remainingMinutes: Int? get() = u16(Reg.REMAINING_TIME)
    val charging: Boolean? get() = flag(Reg.CHARGING)

    val acInputW: Int? get() = u16(Reg.AC_INPUT_POWER)
    val pvInputW: Int? get() = u16(Reg.PV_CHARGE_POWER)
    val totalInputW: Int? get() = u16(Reg.TOTAL_INPUT_POWER)
    val acOutputW: Int? get() = u16(Reg.AC_OUTPUT_POWER)
    val dcOutputW: Int? get() = u16(Reg.DC_POWER)
    // whole watts, though the app's register table says 0.1 W: 19 here matched 19 W of total output on 2026-10-19
    val usbOutputW: Int? get() = u16(Reg.USB_POWER)
    val totalOutputW: Int? get() = u16(Reg.TOTAL_OUTPUT_POWER)

    val acVoltage: Double? get() = u16(Reg.AC_OUTPUT_VOLTAGE)?.let { it / 10.0 }
    val acFrequency: Double? get() = u16(Reg.AC_OUTPUT_FREQUENCY)?.let { it / 10.0 }
    val inverterTemp: Int? get() = registers[Reg.INVERTER_TEMP]?.let { Frame.u16(it, 0).toShort().toInt() }

    val ledMode: Int? get() = u8(Reg.LED_MODE)
    val acOn: Boolean? get() = flag(Reg.AC_SWITCH)
    val dcOn: Boolean? get() = flag(Reg.DC_SWITCH)
    val usbOn: Boolean? get() = flag(Reg.USB_SWITCH)
    val ecoMode: Boolean? get() = flag(Reg.ECO_MODE)
    // 0 is sound on: checked by ear on 2026-10-17 (the Fossibot+ switch shows it the other way round)
    val keySoundOn: Boolean? get() = flag(Reg.KEY_SOUND_OFF)?.not()
    val outputMemory: Boolean? get() = flag(Reg.OUTPUT_MEMORY)

    /**
     * Whether the station may charge from the grid now. The vendor's cloud runs the charge schedule by
     * flipping this: 1 with no schedule and inside a Charge slot, 0 outside one.
     */
    val gridChargingAllowed: Boolean? get() = flag(Reg.SCHEDULED_CHARGE)

    /** A Discharge slot of the cloud's schedule is running: the station keeps AC on until the slot ends. */
    val acScheduled: Boolean? get() = flag(Reg.SCHEDULED_DISCHARGE)

    val chargePowerLimitW: Int? get() = u16(Reg.CHARGE_POWER_LIMIT)
    val chargeUpperLimit: Int? get() = u8(Reg.CHARGE_UPPER_LIMIT)
    val dischargeLowerLimit: Int? get() = u8(Reg.DISCHARGE_LOWER_LIMIT)
    val screenBrightness: Int? get() = u8(Reg.SCREEN_BRIGHTNESS)
    val dcChargeCurrent: Int? get() = u8(Reg.DC_CHARGE_CURRENT)

    /** Indexes into the option lists of the Fossibot+ settings screen, see [Options]. */
    val screenOffTime: Int? get() = u8(Reg.SCREEN_OFF_TIME)
    val shutdownTime: Int? get() = u8(Reg.SHUTDOWN_TIME)
    val acIdleOff: Int? get() = u8(Reg.AC_IDLE_OFF)
    val dcIdleOff: Int? get() = u8(Reg.DC_IDLE_OFF)
    val usbIdleOff: Int? get() = u8(Reg.USB_IDLE_OFF)

    val pvEnergyKwh: Int? get() = u16(Reg.PV_ENERGY)
    val pcsVersion: String? get() = u16(Reg.PCS_VERSION)?.let { "%04x".format(it) }

    val firmwareVersion: String? get() = version(Reg.FIRMWARE_VERSION)
    val bmsVersion: String? get() = version(Reg.BMS_VERSION)

    /** The register's value as the app writes it: one byte, or 16 bits for the wide ones. */
    fun raw(key: Int): Int? = if (key in Reg.WIDE) u16(key) else u8(key)

    private fun u8(key: Int): Int? = registers[key]?.let { it[0].toInt() and 0xff }

    private fun u16(key: Int): Int? = registers[key]?.let { Frame.u16(it, 0) }

    private fun flag(key: Int): Boolean? = u8(key)?.let { it != 0 }

    // the app prints these 4 bytes in reverse, which reads as a date-like version
    private fun version(key: Int): String? =
        registers[key]?.reversed()?.joinToString("-") { "%02x".format(it) }

    companion object {
        /** The ceiling of the remaining time, 100 h; the station reaches it with little or no load. */
        const val REMAINING_MAX = 6000
    }
}
