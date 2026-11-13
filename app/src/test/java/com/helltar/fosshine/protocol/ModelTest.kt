package com.helltar.fosshine.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTest {

    @Test
    fun `serial comes from the AP name`() {
        // an AP name as WifiInfo reports an SSID: in quotes
        assertEquals("F180V012345A0001", Model.serialOf("\"FOSS_F180V012345A0001\"", "FOSS_"))
        assertEquals("F180V012345A0001", Model.serialOf("FOSS_F180V012345A0001", "FOSS_"))
        assertNull(Model.serialOf("<unknown ssid>", "FOSS_"))
        assertNull(Model.serialOf("\"FOSS_\"", "FOSS_"))
        assertNull(Model.serialOf(null, "FOSS_"))
    }

    @Test
    fun `charge power range follows the serial like the app`() {
        assertEquals(1200, Model.chargePowerMax("F180V012345A0001"))
        assertEquals(2000, Model.chargePowerMax("F300V012345A0001"))
        assertEquals(1200, Model.chargePowerMax(null))
    }

    @Test
    fun `model shows only when the serial names it`() {
        assertEquals(true, Model.isF1800("F180V012345A0001"))
        assertEquals(false, Model.isF3000("F180V012345A0001"))
        assertEquals(true, Model.isF3000("F300V012345A0001"))
        assertEquals(false, Model.isF1800(null))
        assertEquals(false, Model.isF3000(null))
    }
}
