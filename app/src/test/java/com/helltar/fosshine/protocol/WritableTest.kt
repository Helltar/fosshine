package com.helltar.fosshine.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritableTest {

    private val f1800 = "F180V012345A0001"
    private val f3000 = "F300V012345A0001"

    private fun assertRange(key: Int, range: IntRange, serial: String? = f1800) {
        for (value in range) assertTrue("$key=$value", Writable.allows(key, value, serial))
        assertFalse("$key=${range.first - 1}", Writable.allows(key, range.first - 1, serial))
        assertFalse("$key=${range.last + 1}", Writable.allows(key, range.last + 1, serial))
    }

    @Test
    fun `every writable register takes the settings screen's range and nothing beside it`() {
        for (key in listOf(Reg.AC_SWITCH, Reg.DC_SWITCH, Reg.USB_SWITCH, Reg.ECO_MODE, Reg.KEY_SOUND_OFF, Reg.OUTPUT_MEMORY)) {
            assertRange(key, 0..1)
        }
        assertRange(Reg.LED_MODE, 0..3)
        assertRange(Reg.CHARGE_UPPER_LIMIT, 60..100)
        assertRange(Reg.DISCHARGE_LOWER_LIMIT, 0..20)
        assertRange(Reg.DC_CHARGE_CURRENT, 1..8)
        assertRange(Reg.SCREEN_BRIGHTNESS, 0..100)
        assertRange(Reg.SCREEN_OFF_TIME, 0..5)
        assertRange(Reg.SHUTDOWN_TIME, 0..4)
        for (key in listOf(Reg.AC_IDLE_OFF, Reg.DC_IDLE_OFF, Reg.USB_IDLE_OFF)) assertRange(key, 0..6)
    }

    @Test
    fun `charge power goes in steps of 100 up to the model's top`() {
        for (serial in listOf(f1800, null)) {
            for (watts in 100..1200 step 100) assertTrue("$watts", Writable.allows(Reg.CHARGE_POWER_LIMIT, watts, serial))
            assertFalse(Writable.allows(Reg.CHARGE_POWER_LIMIT, 0, serial))
            assertFalse(Writable.allows(Reg.CHARGE_POWER_LIMIT, 150, serial))
            assertFalse(Writable.allows(Reg.CHARGE_POWER_LIMIT, 1300, serial))
        }

        assertTrue(Writable.allows(Reg.CHARGE_POWER_LIMIT, 2000, f3000))
        assertFalse(Writable.allows(Reg.CHARGE_POWER_LIMIT, 2100, f3000))
    }

    @Test
    fun `the F3000 has no DC charging current to set`() {
        assertTrue(Writable.hasDcChargeCurrent(f1800))
        assertTrue(Writable.hasDcChargeCurrent(null))
        assertFalse(Writable.hasDcChargeCurrent(f3000))
        for (amps in 1..8) assertFalse("$amps", Writable.allows(Reg.DC_CHARGE_CURRENT, amps, f3000))
    }

    @Test
    fun `no other register is writable`() {
        val writable = setOf(
            Reg.AC_SWITCH, Reg.DC_SWITCH, Reg.USB_SWITCH, Reg.ECO_MODE, Reg.KEY_SOUND_OFF, Reg.OUTPUT_MEMORY,
            Reg.LED_MODE, Reg.CHARGE_POWER_LIMIT, Reg.CHARGE_UPPER_LIMIT, Reg.DISCHARGE_LOWER_LIMIT,
            Reg.DC_CHARGE_CURRENT, Reg.SCREEN_BRIGHTNESS, Reg.SCREEN_OFF_TIME, Reg.SHUTDOWN_TIME,
            Reg.AC_IDLE_OFF, Reg.DC_IDLE_OFF, Reg.USB_IDLE_OFF
        )

        // the whole key byte, the cloud's schedule flags included: seen written, but not by this app
        for (key in 0..255) {
            if (key in writable) continue
            for (value in listOf(0, 1, 100)) assertFalse("$key=$value", Writable.allows(key, value, f1800))
        }
    }
}
