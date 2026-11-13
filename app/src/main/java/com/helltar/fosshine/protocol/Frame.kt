package com.helltar.fosshine.protocol

/**
 * The station's frame, as spoken over TCP in offline mode.
 *
 * Layout, little-endian: u16 total length, u16 kind, u16 body length (entries + CRC), then 6-byte entries
 * (u16 register key, 4 value bytes) and a CRC-16/MODBUS of the entries, sent big-endian.
 */
object Frame {

    const val KIND_TELEMETRY = 0x0b
    const val KIND_WRITE = 0x0c

    private const val HEADER_SIZE = 6
    private const val ENTRY_SIZE = 6
    private const val CRC_SIZE = 2

    /** The fixed reply the app sends after every frame it receives. */
    val ACK: ByteArray = hex("0a001f00040004001f00")

    /** One register write, the only kind of command the app sends. */
    fun write(key: Int, value: Int): ByteArray {
        val entry = byteArrayOf(key.toByte(), 0, value.toByte(), (value shr 8).toByte(), 0, 0)
        return header(KIND_WRITE, entry.size) + entry + crcBytes(entry)
    }

    /** Parses one whole frame; null when the length or CRC does not hold up. */
    fun parse(frame: ByteArray): Parsed? {
        if (frame.size < HEADER_SIZE + CRC_SIZE || u16(frame, 0) != frame.size) return null

        val entries = frame.copyOfRange(HEADER_SIZE, frame.size - CRC_SIZE)
        val crc = frame.copyOfRange(frame.size - CRC_SIZE, frame.size)
        if (!crc.contentEquals(crcBytes(entries))) return null

        val registers = buildMap {
            for (offset in 0..entries.size - ENTRY_SIZE step ENTRY_SIZE) {
                put(u16(entries, offset), entries.copyOfRange(offset + 2, offset + ENTRY_SIZE))
            }
        }

        return Parsed(kind = u16(frame, 2), registers = registers)
    }

    fun crc16Modbus(data: ByteArray): Int {
        var crc = 0xffff
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xff)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xa001 else crc ushr 1 }
        }
        return crc
    }

    internal fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)

    private fun header(kind: Int, entriesSize: Int): ByteArray {
        val bodySize = entriesSize + CRC_SIZE
        return le16(HEADER_SIZE + bodySize) + le16(kind) + le16(bodySize)
    }

    private fun crcBytes(entries: ByteArray): ByteArray {
        val crc = crc16Modbus(entries)
        return byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    private fun le16(value: Int): ByteArray = byteArrayOf(value.toByte(), (value shr 8).toByte())

    class Parsed(val kind: Int, val registers: Map<Int, ByteArray>)
}

internal fun hex(text: String): ByteArray =
    ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
