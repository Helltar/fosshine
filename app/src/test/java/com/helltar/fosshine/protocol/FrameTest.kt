package com.helltar.fosshine.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameTest {

    @Test
    fun `write frames match the ones the station accepted`() {
        // each of these was sent to the real station on 2026-10-17 and took effect
        assertEquals("0e000c000800260001000000e106", Frame.write(Reg.LED_MODE, 1).toHex())
        assertEquals("0e000c0008002600000000001d07", Frame.write(Reg.LED_MODE, 0).toHex())
        assertEquals("0e000c0008002700010000003007", Frame.write(Reg.AC_SWITCH, 1).toHex())
        assertEquals("0e000c000800270000000000cc06", Frame.write(Reg.AC_SWITCH, 0).toHex())
        assertEquals("0e000c000800290000000000e207", Frame.write(Reg.USB_SWITCH, 0).toHex())
    }

    @Test
    fun `sixteen-bit values go low byte first`() {
        assertEquals("0e000c0008002a0090010000117b", Frame.write(Reg.CHARGE_POWER_LIMIT, 400).toHex())
    }

    @Test
    fun `real telemetry frame parses`() {
        val frame = Frame.parse(hex(TELEMETRY))
        assertNotNull(frame)
        assertEquals(Frame.KIND_TELEMETRY, frame!!.kind)

        val t = Telemetry(frame.registers)
        assertEquals(84, t.soc)
        assertEquals(26, t.batteryTempMax)
        assertEquals(182, t.remainingMinutes)
        assertEquals(245, t.acOutputW)
        assertEquals(231.6, t.acVoltage!!, 0.001)
        assertEquals(50.0, t.acFrequency!!, 0.001)
        assertEquals(true, t.acOn)
        assertEquals(false, t.usbOn)
        assertEquals(400, t.chargePowerLimitW)
        assertEquals(100, t.chargeUpperLimit)
        assertEquals(80, t.screenBrightness)
        assertEquals("10-01-02-07", t.firmwareVersion)
        assertEquals(400, t.raw(Reg.CHARGE_POWER_LIMIT))
        assertEquals(true, t.gridChargingAllowed)
        assertEquals(false, t.acScheduled)
    }

    @Test
    fun `usb power is whole watts`() {
        // sent at 02:45:51 on 2026-10-19 with a phone charging from USB and every other output off
        val t = Telemetry(Frame.parse(hex(TELEMETRY_USB_LOAD))!!.registers)
        assertEquals(19, t.usbOutputW)
        assertEquals(t.totalOutputW, t.usbOutputW)
    }

    @Test
    fun `schedule flags read as the cloud left them`() {
        // sent at 01:06 on 2026-10-19, inside a Discharge slot that followed a Charge slot ending at 01:03
        val t = Telemetry(Frame.parse(hex(TELEMETRY_SCHEDULED))!!.registers)
        assertEquals(false, t.gridChargingAllowed)
        assertEquals(true, t.acScheduled)
        assertEquals(true, t.acOn)
    }

    @Test
    fun `byte registers ignore the bytes above the first`() {
        // register 43 has been seen as 00 01 00 00; the app reads it as one byte, so 0
        val t = Telemetry(mapOf(Reg.OUTPUT_MEMORY to byteArrayOf(0, 1, 0, 0), Reg.CHARGE_POWER_LIMIT to byteArrayOf(0x84.toByte(), 3, 0, 0)))
        assertEquals(0, t.raw(Reg.OUTPUT_MEMORY))
        assertEquals(false, t.outputMemory)
        assertEquals(900, t.raw(Reg.CHARGE_POWER_LIMIT))
    }

    @Test
    fun `corrupted frame is rejected`() {
        val bytes = hex(TELEMETRY)
        bytes[10] = (bytes[10] + 1).toByte()
        assertNull(Frame.parse(bytes))
    }

    @Test
    fun `reader splits and joins stream chunks`() {
        val frame = hex(TELEMETRY)
        val stream = byteArrayOf(0x00, 0x7f) + frame + frame
        val reader = FrameReader()

        val first = reader.feed(stream.copyOfRange(0, 100))
        val rest = reader.feed(stream.copyOfRange(100, stream.size))

        assertEquals(0, first.size)
        assertEquals(2, rest.size)
        rest.forEach { assertNotNull(Frame.parse(it)) }
    }

    private companion object {
        const val TELEMETRY =
            "e0000b00da0001005400000002001a0000000300b60000000400000000000500280126101200161000001300000000" +
                "001400f500000015000c0900001600f40100001700000000002200000000002300f5000000240000000000250000" +
                "0000002600000000002700010000002800000000002900000000002a00900100002b00000000002c00020000002d" +
                "00020000002e00000000002f00070201103000000000003100640000003200000000003300000000003400500000" +
                "003500080000003600010000003700000000003800030000003900030000003a0004000000b14e"

        const val TELEMETRY_USB_LOAD =
            "e0000b00da0001006200000002001c0000000300f81600000400000000000500280126101200161000001300000000" +
                "001400000000001500000000001600000000001700000000002200000000002300130000002400000000002500130000" +
                "002600000000002700000000002800000000002900010000002a00900100002b00000000002c00020000002d00020000" +
                "002e00000000002f00070201103000000000003100640000003200000000003300000000003400230000003500080000" +
                "003600010000003700000000003800030000003900030000003a0004000000a1ea"

        const val TELEMETRY_SCHEDULED =
            "e0000b00da000100640000000200190000000300701700000400000000000500280126101200161000001300270000" +
                "001400270000001500870800001600f30100001700000000002200270000002300270000002400000000002500000000" +
                "002600000000002700010000002800000000002900000000002a00900100002b00000000002c00020000002d00030000" +
                "002e00000000002f00070201103000000000003100640000003200000000003300000000003400230000003500080000" +
                "003600000000003700010000003800030000003900030000003a0004000000e607"
    }
}
