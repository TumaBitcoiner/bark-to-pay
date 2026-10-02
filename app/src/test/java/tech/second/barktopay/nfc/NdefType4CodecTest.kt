package tech.second.barktopay.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NdefType4CodecTest {

    @Test
    fun `uri round-trips through ndef file`() {
        val uri = "bitcoin:?tark=tark1spikeplaceholder000000000000000000000000000" +
            "&amount=0.00010000&label=Phase%200%20Spike"

        val ndefFile = NdefType4Codec.ndefFileFromUri(uri)
        val decoded = NdefType4Codec.uriFromNdefFile(ndefFile)

        assertEquals(uri, decoded)
    }

    @Test
    fun `ndef file starts with correct NLEN`() {
        val uri = "bitcoin:?tark=abc"
        val ndefFile = NdefType4Codec.ndefFileFromUri(uri)

        val nlen = ((ndefFile[0].toInt() and 0xFF) shl 8) or (ndefFile[1].toInt() and 0xFF)
        assertEquals(ndefFile.size - 2, nlen)
    }

    @Test
    fun `capability container points at NDEF file E104`() {
        val cc = NdefType4Codec.capabilityContainer()
        assertEquals(0x0F, cc.size)
        // File control TLV: T=0x04, L=0x06, file ID E104
        assertEquals(0x04.toByte(), cc[7])
        assertEquals(0x06.toByte(), cc[8])
        assertEquals(0xE1.toByte(), cc[9])
        assertEquals(0x04.toByte(), cc[10])
    }

    @Test
    fun `round-trip is byte-identical on re-encode`() {
        val uri = "bitcoin:?tark=tark1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq0&label=caf%C3%A9"
        val first = NdefType4Codec.ndefFileFromUri(uri)
        val second = NdefType4Codec.ndefFileFromUri(NdefType4Codec.uriFromNdefFile(first))
        assertArrayEquals(first, second)
    }

    @Test
    fun `rejects empty ndef file`() {
        assertThrows(IllegalArgumentException::class.java) {
            NdefType4Codec.uriFromNdefFile(byteArrayOf())
        }
    }

    @Test
    fun `rejects truncated ndef file`() {
        val uri = "bitcoin:?tark=tark1abcdef"
        val full = NdefType4Codec.ndefFileFromUri(uri)
        assertThrows(IllegalArgumentException::class.java) {
            NdefType4Codec.uriFromNdefFile(full.copyOf(full.size / 2))
        }
    }
}
