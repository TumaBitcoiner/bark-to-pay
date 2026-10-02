package tech.second.barktopay.nfc

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object NdefType4Codec {
    private const val NDEF_FILE_ID_MSB: Byte = 0xE1.toByte()
    private const val NDEF_FILE_ID_LSB: Byte = 0x04.toByte()

    fun capabilityContainer(): ByteArray {
        return byteArrayOf(
            0x00, 0x0F,
            0x20,
            0x00, 0x3B,
            0x00, 0x34,
            0x04, 0x06,
            NDEF_FILE_ID_MSB,
            NDEF_FILE_ID_LSB,
            0x00, 0x3B,
            0x00, 0x00
        )
    }

    fun ndefFileFromUri(uri: String): ByteArray {
        val uriBytes = uri.toByteArray(StandardCharsets.UTF_8)
        val ndefMessage = ByteArray(1 + 1 + 1 + TYPE_U.size + 1 + uriBytes.size)

        var index = 0
        ndefMessage[index++] = 0xD1.toByte()
        ndefMessage[index++] = 0x01
        ndefMessage[index++] = (1 + uriBytes.size).toByte()
        ndefMessage[index++] = TYPE_U[0]
        ndefMessage[index++] = 0x00
        System.arraycopy(uriBytes, 0, ndefMessage, index, uriBytes.size)

        val out = ByteBuffer.allocate(2 + ndefMessage.size)
        out.putShort(ndefMessage.size.toShort())
        out.put(ndefMessage)
        return out.array()
    }

    fun uriFromNdefFile(ndefFile: ByteArray): String {
        if (ndefFile.size < 2) {
            throw IllegalArgumentException("NDEF file too short")
        }
        val payloadLength = ((ndefFile[0].toInt() and 0xFF) shl 8) or (ndefFile[1].toInt() and 0xFF)
        if (payloadLength <= 0 || ndefFile.size < 2 + payloadLength) {
            throw IllegalArgumentException("Invalid NDEF payload length")
        }

        val msg = ndefFile.copyOfRange(2, 2 + payloadLength)
        if (msg.size < 5) {
            throw IllegalArgumentException("NDEF message too short")
        }
        if (msg[0] != 0xD1.toByte()) {
            throw IllegalArgumentException("Unsupported NDEF header")
        }
        if (msg[1].toInt() != 0x01) {
            throw IllegalArgumentException("Unsupported type length")
        }
        val typeLength = msg[1].toInt() and 0xFF
        val payloadSize = msg[2].toInt() and 0xFF
        val typeStart = 3
        val payloadStart = typeStart + typeLength
        if (msg.size < payloadStart + payloadSize) {
            throw IllegalArgumentException("Invalid NDEF URI payload")
        }
        if (msg[typeStart] != TYPE_U[0]) {
            throw IllegalArgumentException("Unsupported NDEF record type")
        }

        val uriIdCode = msg[payloadStart].toInt() and 0xFF
        if (uriIdCode != 0x00) {
            throw IllegalArgumentException("Unsupported URI prefix code")
        }

        return String(msg, payloadStart + 1, payloadSize - 1, StandardCharsets.UTF_8)
    }
}

private val TYPE_U = byteArrayOf(0x55)
