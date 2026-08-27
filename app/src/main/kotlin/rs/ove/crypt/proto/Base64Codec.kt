package rs.ove.crypt.proto

import java.io.ByteArrayOutputStream

object Base64Codec {
    private val encoding = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
    private val decoding = IntArray(256) { -1 }.also { values ->
        encoding.forEachIndexed { index, character -> values[character.code] = index }
        values['='.code] = -2
    }

    @JvmStatic fun encode(input: ByteArray?): String {
        val bytes = input ?: return ""
        if (bytes.isEmpty()) return ""
        return buildString((bytes.size + 2) / 3 * 4) {
            var index = 0
            while (index < bytes.size) {
                val first = bytes[index++].toInt() and 0xff
                val second = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
                val third = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
                append(encoding[first ushr 2])
                append(encoding[((first and 3) shl 4) or (if (second < 0) 0 else second ushr 4)])
                append(if (second < 0) '=' else encoding[((second and 15) shl 2) or (if (third < 0) 0 else third ushr 6)])
                append(if (third < 0) '=' else encoding[third and 63])
            }
        }
    }

    @JvmStatic fun decode(input: String?): ByteArray {
        if (input.isNullOrEmpty()) return ByteArray(0)
        val output = ByteArrayOutputStream(input.length * 3 / 4)
        val block = IntArray(4)
        var count = 0
        input.forEach { character ->
            if (character.code > 255) throw IllegalArgumentException("invalid base64 character")
            val value = decoding[character.code]
            if (value == -1) {
                if (character.isWhitespace()) return@forEach
                throw IllegalArgumentException("invalid base64 character")
            }
            block[count++] = value
            if (count == 4) { decodeBlock(block, output); count = 0 }
        }
        if (count != 0) throw IllegalArgumentException("invalid base64 length")
        return output.toByteArray()
    }

    private fun decodeBlock(block: IntArray, output: ByteArrayOutputStream) {
        if (block[0] < 0 || block[1] < 0 || (block[2] == -2 && block[3] != -2)) throw IllegalArgumentException("invalid base64 padding")
        val second = if (block[2] == -2) 0 else block[2]
        val third = if (block[3] == -2) 0 else block[3]
        output.write((block[0] shl 2) or (block[1] ushr 4))
        if (block[2] != -2) output.write(((block[1] and 15) shl 4) or (second ushr 2))
        if (block[3] != -2) output.write(((second and 3) shl 6) or third)
    }
}
