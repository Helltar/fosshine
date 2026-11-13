"""Fossibot+ frame codec, as read from the Fossibot+ 1.0.23 app (utils/power-hook.js)."""
import struct
import sys


def crc16_modbus(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc


def parse(frame: bytes) -> dict:
    total, kind, body_len = struct.unpack_from("<HHH", frame, 0)
    body, crc = frame[6:-2], frame[-2:]
    regs = {}
    for i in range(0, len(body) - len(body) % 6, 6):
        key, = struct.unpack_from("<H", body, i)
        regs[key] = body[i + 2:i + 6]
    return {"total": total, "len": len(frame), "kind": kind, "body_len": body_len,
            "crc": crc.hex(), "crc_body_be": crc16_modbus(body).to_bytes(2, "big").hex(),
            "crc_all_be": crc16_modbus(frame[:-2]).to_bytes(2, "big").hex(), "regs": regs}


def write_cmd(key: int, value: int) -> bytes:
    # the app writes one register per frame: key, 0, value lo, value hi, 0, 0
    payload = bytes([key, 0, value & 0xFF, value >> 8, 0, 0])
    return struct.pack("<HHH", 14, 12, 8) + payload + crc16_modbus(payload).to_bytes(2, "big")


if __name__ == "__main__":
    for h in sys.argv[1:]:
        p = parse(bytes.fromhex(h))
        regs = p.pop("regs")
        print(p)
        print({k: v.hex() for k, v in regs.items()})
