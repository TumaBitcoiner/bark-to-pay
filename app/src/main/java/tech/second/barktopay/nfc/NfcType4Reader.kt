package tech.second.barktopay.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep

object NfcType4Reader {
    fun readUriFromTag(tag: Tag): String {
        val isoDep = IsoDep.get(tag) ?: throw IllegalStateException("Tag is not IsoDep compatible")
        isoDep.connect()
        try {
            requireOk(isoDep.transceive(SELECT_NDEF_AID_APDU), "SELECT AID failed")
            requireOk(isoDep.transceive(SELECT_CC_FILE_APDU), "SELECT CC failed")
            requireOk(isoDep.transceive(READ_CC_FILE_APDU), "READ CC failed")

            requireOk(isoDep.transceive(SELECT_NDEF_FILE_APDU), "SELECT NDEF file failed")
            val nlenResponse = isoDep.transceive(READ_NLEN_APDU)
            val nlenData = extractData(nlenResponse, "READ NLEN failed")
            if (nlenData.size != 2) {
                throw IllegalStateException("Invalid NLEN response length")
            }
            val nlen = ((nlenData[0].toInt() and 0xFF) shl 8) or (nlenData[1].toInt() and 0xFF)
            val fullResponse = isoDep.transceive(buildReadBinaryApdu(offset = 0, size = nlen + 2))
            val ndefFile = extractData(fullResponse, "READ NDEF failed")
            return NdefType4Codec.uriFromNdefFile(ndefFile)
        } finally {
            isoDep.close()
        }
    }

    private fun requireOk(response: ByteArray, message: String) {
        if (!hasSuccessStatus(response)) {
            throw IllegalStateException("$message (${statusWordHex(response)})")
        }
    }

    private fun extractData(response: ByteArray, message: String): ByteArray {
        if (!hasSuccessStatus(response)) {
            throw IllegalStateException("$message (${statusWordHex(response)})")
        }
        return response.copyOf(response.size - 2)
    }

    private fun hasSuccessStatus(response: ByteArray): Boolean {
        return response.size >= 2 &&
            response[response.size - 2] == 0x90.toByte() &&
            response[response.size - 1] == 0x00.toByte()
    }

    private fun statusWordHex(response: ByteArray): String {
        if (response.size < 2) return "<none>"
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        return String.format("%02X%02X", sw1, sw2)
    }

    private fun buildReadBinaryApdu(offset: Int, size: Int): ByteArray {
        return byteArrayOf(
            0x00,
            0xB0.toByte(),
            ((offset ushr 8) and 0xFF).toByte(),
            (offset and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
    }
}

private val SELECT_NDEF_AID_APDU = byteArrayOf(
    0x00,
    0xA4.toByte(),
    0x04,
    0x00,
    0x07,
    0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
    0x00
)

private val SELECT_CC_FILE_APDU = byteArrayOf(
    0x00,
    0xA4.toByte(),
    0x00,
    0x0C,
    0x02,
    0xE1.toByte(),
    0x03,
    0x00
)

private val READ_CC_FILE_APDU = byteArrayOf(
    0x00,
    0xB0.toByte(),
    0x00,
    0x00,
    0x0F
)

private val SELECT_NDEF_FILE_APDU = byteArrayOf(
    0x00,
    0xA4.toByte(),
    0x00,
    0x0C,
    0x02,
    0xE1.toByte(),
    0x04,
    0x00
)

private val READ_NLEN_APDU = byteArrayOf(
    0x00,
    0xB0.toByte(),
    0x00,
    0x00,
    0x02
)
