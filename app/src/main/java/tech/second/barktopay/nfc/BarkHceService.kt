package tech.second.barktopay.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class BarkHceService : HostApduService() {
    private var selectedFile: FileId? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (commandApdu.size < 4) {
            return SW_WRONG_LENGTH
        }

        val ins = commandApdu[1].toInt() and 0xFF
        return when (ins) {
            INS_SELECT -> handleSelect(commandApdu)
            INS_READ_BINARY -> handleReadBinary(commandApdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = null
    }

    private fun handleSelect(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) {
            return SW_WRONG_LENGTH
        }

        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) {
            return SW_WRONG_LENGTH
        }

        val data = apdu.copyOfRange(5, 5 + lc)

        if (p1 == 0x04 && p2 == 0x00 && data.contentEquals(SELECT_NDEF_AID_DATA)) {
            selectedFile = null
            return SW_OK
        }

        if (p1 == 0x00 && p2 == 0x0C && data.size == 2) {
            selectedFile = when {
                data.contentEquals(CC_FILE_ID) -> FileId.CC
                data.contentEquals(NDEF_FILE_ID) -> FileId.NDEF
                else -> null
            }
            return if (selectedFile == null) SW_FILE_NOT_FOUND else SW_OK
        }

        return SW_FUNC_NOT_SUPPORTED
    }

    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) {
            return SW_WRONG_LENGTH
        }
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val le = apdu[4].toInt() and 0xFF

        val fileData = when (selectedFile) {
            FileId.CC -> NdefType4Codec.capabilityContainer()
            FileId.NDEF -> {
                val uri = HcePayloadStore.loadCurrentUri(this)
                NdefType4Codec.ndefFileFromUri(uri)
            }
            null -> return SW_CONDITIONS_NOT_SATISFIED
        }

        if (offset > fileData.size) {
            return SW_WRONG_P1P2
        }

        val endExclusive = if (le == 0) fileData.size else minOf(fileData.size, offset + le)
        val chunk = fileData.copyOfRange(offset, endExclusive)
        return chunk + SW_OK
    }

    private enum class FileId {
        CC,
        NDEF
    }
}

private const val INS_SELECT = 0xA4
private const val INS_READ_BINARY = 0xB0

private val SELECT_NDEF_AID_DATA = byteArrayOf(
    0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
)

private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03.toByte())
private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04.toByte())

private val SW_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
private val SW_FUNC_NOT_SUPPORTED = byteArrayOf(0x6A.toByte(), 0x81.toByte())
private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
private val SW_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00.toByte())
private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
