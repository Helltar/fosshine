"""Register map of the F1800, translated from the Fossibot+ 1.0.23 app (utils/power-hook.js).

Each entry: key -> (name, type, scale, unit). scale multiplies the raw integer; None means show raw.
"""

REGS = {
    1: ("soc", "u8", 1, "%"),
    2: ("battery_temp_max", "i8", 1, "C"),
    3: ("remaining_time", "u16", 1, "min"),
    4: ("charging", "u8", None, ""),
    5: ("bms_version", "ver", None, ""),
    6: ("battery_temp_min", "i16", 1, "C"),
    7: ("bms_mos_temp", "i16", 1, "C"),
    8: ("pack_voltage", "u16", 0.1, "V"),
    9: ("current", "i32", 0.001, "A"),
    10: ("bus_voltage", "u16", 0.1, "V"),
    11: ("balance_state", "u32", None, ""),
    12: ("bms_fault", "u32", None, ""),
    18: ("pcs_mppt_version", "hex16", None, ""),
    19: ("ac_input_power", "u16", 1, "W"),
    20: ("ac_output_power", "u16", 1, "W"),
    21: ("ac_output_voltage", "u16", 0.1, "V"),
    22: ("ac_output_frequency", "u16", 0.1, "Hz"),
    23: ("pv_charge_power", "u16", 1, "W"),
    24: ("inverter_temp", "i16", 1, "C"),
    25: ("mos_temp", "i16", 1, "C"),
    26: ("pcs_fault", "u32", None, ""),
    27: ("pv_input_voltage", "u16", 0.1, "V"),
    28: ("pv_input_current", "i32", 0.001, "A"),
    29: ("pv_mode", "u8", None, ""),
    30: ("pv_fault", "u32", None, ""),
    34: ("total_input_power", "u16", 1, "W"),
    35: ("total_output_power", "u16", 1, "W"),
    36: ("dc_power", "u16", 1, "W"),
    37: ("usb_power", "u16", 0.1, "W"),
    38: ("led_mode", "u8", None, "0 off, 1 on, 2 sos, 3 strobe"),
    39: ("ac_switch", "bool", None, ""),
    40: ("dc_switch", "bool", None, ""),
    41: ("usb_switch", "bool", None, ""),
    42: ("charge_power_limit", "u16", 1, "W"),
    43: ("ac_power_on_memory", "u8", None, ""),
    44: ("screen_timeout", "u8", None, "0 never, 1 30s, 2 1m, 3 5m, 4 10m, 5 30m"),
    45: ("shutdown_time", "u8", None, "0 never, 1 5m, 2 10m, 3 1h, 4 8h"),
    46: ("eco_mode", "bool", None, "0 ups, 1 eco"),
    47: ("firmware_version", "ver", None, ""),
    48: ("pv_energy", "u16", 1, "kWh"),
    49: ("charge_upper_limit", "u8", 1, "%"),
    50: ("discharge_lower_limit", "u8", 1, "%"),
    51: ("key_sound_off", "u8", None, "0 sound on, 1 sound off"),
    52: ("screen_brightness", "u8", 1, "%"),
    53: ("dc_charge_current", "u8", 1, "A"),
    54: ("scheduled_charge", "u8", None, ""),
    55: ("scheduled_discharge", "u8", None, ""),
    56: ("dc_idle_off", "u8", None, "0 never, 1 30m, 2 1h, 3 4h, 4 8h, 5 12h, 6 24h"),
    57: ("usb_idle_off", "u8", None, "0 never, 1 30m, 2 1h, 3 4h, 4 8h, 5 12h, 6 24h"),
    58: ("ac_idle_off", "u8", None, "0 never, 1 30m, 2 1h, 3 4h, 4 8h, 5 12h, 6 24h"),
}

NAMES = {v[0]: k for k, v in REGS.items()}


def value(kind: str, raw: bytes):
    u32 = int.from_bytes(raw, "little")
    match kind:
        case "u8" | "bool":
            return raw[0]
        case "i8":
            return raw[0] - 256 if raw[0] > 127 else raw[0]
        case "u16":
            return u32 & 0xFFFF
        case "i16":
            v = u32 & 0xFFFF
            return v - 0x10000 if v >= 0x8000 else v
        case "hex16":
            return f"{u32 & 0xFFFF:04x}"
        case "i32":
            # the app sign-extends from 16 bits when the upper half is all 0 or all 1
            if raw[2:] in (b"\x00\x00", b"\xff\xff"):
                v = u32 & 0xFFFF
                return v - 0x10000 if v >= 0x8000 else v
            return u32 - (1 << 32) if u32 >= 1 << 31 else u32
        case "ver":
            return "-".join(f"{b:02x}" for b in reversed(raw))
        case _:
            return u32


def decode(regs: dict[int, bytes]) -> dict[str, object]:
    out = {}
    for key, raw in sorted(regs.items()):
        if key not in REGS:
            out[f"reg{key}"] = raw.hex()
            continue
        name, kind, scale, unit = REGS[key]
        v = value(kind, raw)
        if scale is not None and isinstance(v, int):
            v = round(v * scale, 3)
            out[name] = f"{v} {unit}"
        else:
            out[name] = v
    return out
