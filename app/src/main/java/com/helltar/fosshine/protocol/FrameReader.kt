package com.helltar.fosshine.protocol

/**
 * Cuts a TCP byte stream into frames by their length prefix.
 *
 * A read can hold part of a frame or several of them; bytes that do not start a plausible frame are
 * dropped one at a time until the stream lines up again.
 */
class FrameReader {

    private var buffer = ByteArray(0)

    fun feed(chunk: ByteArray): List<ByteArray> {
        buffer += chunk
        val frames = mutableListOf<ByteArray>()

        while (buffer.size >= 2) {
            val length = Frame.u16(buffer, 0)
            if (length !in MIN_FRAME..MAX_FRAME) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < length) break

            frames += buffer.copyOfRange(0, length)
            buffer = buffer.copyOfRange(length, buffer.size)
        }

        return frames
    }

    private companion object {
        const val MIN_FRAME = 8
        const val MAX_FRAME = 4096
    }
}
