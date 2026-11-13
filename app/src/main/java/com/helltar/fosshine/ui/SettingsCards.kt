package com.helltar.fosshine.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.helltar.fosshine.R
import com.helltar.fosshine.protocol.Options
import com.helltar.fosshine.protocol.Reg
import com.helltar.fosshine.protocol.Telemetry
import com.helltar.fosshine.protocol.Writable

@Composable
internal fun OutputsCard(t: Telemetry, c: Controls) {
    SectionCard(stringResource(R.string.outputs)) {
        SwitchRow(
            title = stringResource(R.string.out_ac),
            subtitle = listOfNotNull(
                if (t.acOn == true) "${t.acOutputW ?: 0} W · ${oneDecimal(t.acVoltage)} V · ${oneDecimal(t.acFrequency)} Hz" else null,
                if (t.acScheduled == true) stringResource(R.string.ac_scheduled) else null
            ).joinToString(" · ").ifEmpty { null },
            checked = t.acOn == true,
            busy = Reg.AC_SWITCH in c.pending,
            enabled = c.enabled
        ) { c.write(Reg.AC_SWITCH, if (it) 1 else 0) }
        SwitchRow(
            title = stringResource(R.string.out_dc),
            subtitle = if (t.dcOn == true) "${t.dcOutputW ?: 0} W" else null,
            checked = t.dcOn == true,
            busy = Reg.DC_SWITCH in c.pending,
            enabled = c.enabled
        ) { c.write(Reg.DC_SWITCH, if (it) 1 else 0) }
        SwitchRow(
            title = stringResource(R.string.out_usb),
            subtitle = if (t.usbOn == true) "${t.usbOutputW ?: 0} W" else null,
            checked = t.usbOn == true,
            busy = Reg.USB_SWITCH in c.pending,
            enabled = c.enabled
        ) { c.write(Reg.USB_SWITCH, if (it) 1 else 0) }
    }
}

@Composable
internal fun LightCard(t: Telemetry, c: Controls) {
    SectionCard(stringResource(R.string.light)) {
        val labels = listOf(R.string.light_off, R.string.light_on, R.string.light_sos, R.string.light_strobe)
        Choice(labels.map { stringResource(it) }, t.ledMode ?: 0, c.enabled(Reg.LED_MODE)) {
            c.write(Reg.LED_MODE, it)
        }
    }
}

@Composable
internal fun ChargingCard(t: Telemetry, c: Controls, serial: String?) {
    SectionCard(stringResource(R.string.charging_settings)) {
        if (t.gridChargingAllowed == false) {
            Text(
                stringResource(R.string.grid_charging_paused),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        // the ranges and steps are the Fossibot+ settings screen's, which tells the models apart by serial
        ValueSlider(stringResource(R.string.charge_power_limit), "W", t.chargePowerLimitW, Writable.chargePower(serial), Writable.CHARGE_POWER_STEP, c.enabled(Reg.CHARGE_POWER_LIMIT)) {
            c.write(Reg.CHARGE_POWER_LIMIT, it)
        }
        ValueSlider(stringResource(R.string.charge_upper_limit), "%", t.chargeUpperLimit, Writable.CHARGE_UPPER_LIMIT, 1, c.enabled(Reg.CHARGE_UPPER_LIMIT)) {
            c.write(Reg.CHARGE_UPPER_LIMIT, it)
        }
        ValueSlider(stringResource(R.string.discharge_lower_limit), "%", t.dischargeLowerLimit, Writable.DISCHARGE_LOWER_LIMIT, 1, c.enabled(Reg.DISCHARGE_LOWER_LIMIT)) {
            c.write(Reg.DISCHARGE_LOWER_LIMIT, it)
        }
        if (Writable.hasDcChargeCurrent(serial)) {
            ValueSlider(stringResource(R.string.dc_charge_current), "A", t.dcChargeCurrent, Writable.DC_CHARGE_CURRENT, 1, c.enabled(Reg.DC_CHARGE_CURRENT)) {
                c.write(Reg.DC_CHARGE_CURRENT, it)
            }
        }

        Text(stringResource(R.string.mode), style = MaterialTheme.typography.bodyLarge)
        Choice(
            listOf(stringResource(R.string.mode_ups), stringResource(R.string.mode_eco)),
            if (t.ecoMode == true) 1 else 0,
            c.enabled(Reg.ECO_MODE)
        ) { c.write(Reg.ECO_MODE, it) }
    }
}

@Composable
internal fun TimersCard(t: Telemetry, c: Controls) {
    SectionCard(stringResource(R.string.timers)) {
        OptionRow(
            title = stringResource(R.string.screen_off_time),
            hint = stringResource(R.string.screen_off_hint),
            options = Options.SCREEN_OFF_TIME,
            never = R.string.screen_always_on,
            selected = t.screenOffTime,
            confirmNever = null,
            enabled = c.enabled(Reg.SCREEN_OFF_TIME)
        ) { c.write(Reg.SCREEN_OFF_TIME, it) }
        OptionRow(
            title = stringResource(R.string.shutdown_time),
            hint = stringResource(R.string.shutdown_hint),
            options = Options.SHUTDOWN_TIME,
            never = R.string.never,
            selected = t.shutdownTime,
            confirmNever = R.string.confirm_never_shutdown,
            enabled = c.enabled(Reg.SHUTDOWN_TIME)
        ) { c.write(Reg.SHUTDOWN_TIME, it) }

        listOf(
            Triple(R.string.ac_idle_off, Reg.AC_IDLE_OFF, t.acIdleOff),
            Triple(R.string.dc_idle_off, Reg.DC_IDLE_OFF, t.dcIdleOff),
            Triple(R.string.usb_idle_off, Reg.USB_IDLE_OFF, t.usbIdleOff)
        ).forEach { (title, key, selected) ->
            OptionRow(
                title = stringResource(title),
                hint = null,
                options = Options.IDLE_OFF,
                never = R.string.never,
                selected = selected,
                confirmNever = R.string.confirm_never_idle,
                enabled = c.enabled(key)
            ) { c.write(key, it) }
        }

        Text(stringResource(R.string.idle_off_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun DeviceCard(t: Telemetry, c: Controls, serial: String?, onKeySound: (Boolean) -> Unit) {
    SectionCard(stringResource(R.string.device)) {
        ValueSlider(stringResource(R.string.screen_brightness), "%", t.screenBrightness, Writable.SCREEN_BRIGHTNESS, 5, c.enabled(Reg.SCREEN_BRIGHTNESS)) {
            c.write(Reg.SCREEN_BRIGHTNESS, it)
        }
        SwitchRow(
            title = stringResource(R.string.key_sound),
            subtitle = null,
            checked = t.keySoundOn == true,
            busy = Reg.KEY_SOUND_OFF in c.pending,
            enabled = c.enabled,
            onChange = onKeySound
        )
        SwitchRow(
            title = stringResource(R.string.output_memory),
            subtitle = stringResource(R.string.output_memory_hint),
            checked = t.outputMemory == true,
            busy = Reg.OUTPUT_MEMORY in c.pending,
            enabled = c.enabled
        ) { c.write(Reg.OUTPUT_MEMORY, if (it) 1 else 0) }

        HorizontalDivider()

        // the F1800 does not send every register the app knows, so rows without a value are left out
        t.inverterTemp?.let { InfoRow(stringResource(R.string.inverter_temp), "$it °C") }
        t.pvEnergyKwh?.let { InfoRow(stringResource(R.string.pv_energy), "$it kWh") }
        serial?.let { InfoRow(stringResource(R.string.serial), it) }
        InfoRow(stringResource(R.string.firmware), t.firmwareVersion)
        InfoRow(stringResource(R.string.pcs), t.pcsVersion)
        InfoRow(stringResource(R.string.bms), t.bmsVersion)
    }
}
