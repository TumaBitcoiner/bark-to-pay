package tech.second.barktopay.bip321

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Bip321Test {

    private val addr = "tark1spikeplaceholder000000000000000000000000000"

    @Test
    fun `build with amount and label`() {
        val uri = Bip321.build(addr, 10_000uL, "Phase 0 Spike")
        assertEquals(
            "bitcoin:?tark=$addr&amount=0.0001&label=Phase%200%20Spike",
            uri
        )
    }

    @Test
    fun `build without amount or label`() {
        assertEquals("bitcoin:?tark=$addr", Bip321.build(addr, null, null))
        assertEquals("bitcoin:?tark=$addr", Bip321.build(addr, null, "  "))
    }

    @Test
    fun `hrp extracted from address`() {
        assertEquals("tark", Bip321.hrpOf(addr))
        assertEquals("ark", Bip321.hrpOf("ark1qqqqqqqqqqqq"))
    }

    @Test
    fun `sats to btc conversions`() {
        assertEquals("0.00000001", Bip321.satsToBtc(1uL))
        assertEquals("0.0001", Bip321.satsToBtc(10_000uL))
        assertEquals("1", Bip321.satsToBtc(100_000_000uL))
        assertEquals("1.23456789", Bip321.satsToBtc(123_456_789uL))
        assertEquals("21000000", Bip321.satsToBtc(2_100_000_000_000_000uL))
    }

    @Test
    fun `btc to sats conversions`() {
        assertEquals(1uL, Bip321.btcToSats("0.00000001"))
        assertEquals(10_000uL, Bip321.btcToSats("0.0001"))
        assertEquals(100_000_000uL, Bip321.btcToSats("1"))
        assertEquals(100_000_000uL, Bip321.btcToSats("1.0"))
        assertEquals(123_456_789uL, Bip321.btcToSats("1.23456789"))
    }

    @Test
    fun `btc to sats rejects bad input`() {
        assertThrows(Bip321.InvalidUriException::class.java) { Bip321.btcToSats("1.000000001") } // >8dp
        assertThrows(Bip321.InvalidUriException::class.java) { Bip321.btcToSats("abc") }
        assertThrows(Bip321.InvalidUriException::class.java) { Bip321.btcToSats("-1") }
        assertThrows(Bip321.InvalidUriException::class.java) { Bip321.btcToSats("") }
    }

    @Test
    fun `parse round-trips build output`() {
        val request = Bip321.PaymentRequest(addr, 50_000uL, "Coffee & cake")
        val parsed = Bip321.parse(Bip321.build(addr, request.amountSats, request.label))
        assertEquals(request, parsed)
    }

    @Test
    fun `parse is case-insensitive for scheme`() {
        val parsed = Bip321.parse("BITCOIN:?tark=$addr")
        assertEquals(addr, parsed.address)
    }

    @Test
    fun `parse ignores unknown optional keys`() {
        val parsed = Bip321.parse("bitcoin:?tark=$addr&something=xyz&amount=0.0001")
        assertEquals(addr, parsed.address)
        assertEquals(10_000uL, parsed.amountSats)
        assertNull(parsed.label)
    }

    @Test
    fun `parse rejects unknown required keys`() {
        assertThrows(Bip321.InvalidUriException::class.java) {
            Bip321.parse("bitcoin:?tark=$addr&req-lightning=lnbc1xyz")
        }
    }

    @Test
    fun `parse rejects missing address and wrong scheme`() {
        assertThrows(Bip321.InvalidUriException::class.java) {
            Bip321.parse("bitcoin:?amount=0.0001")
        }
        assertThrows(Bip321.InvalidUriException::class.java) {
            Bip321.parse("https:?tark=$addr")
        }
        assertThrows(Bip321.InvalidUriException::class.java) {
            Bip321.parse("bitcoin:$addr")
        }
    }

    @Test
    fun `parse rejects duplicate address keys`() {
        assertThrows(Bip321.InvalidUriException::class.java) {
            Bip321.parse("bitcoin:?tark=$addr&ark=ark1qqqqqqqqqqqq")
        }
    }

    @Test
    fun `parseAddressOrUri accepts raw address with fallback amount`() {
        val parsed = Bip321.parseAddressOrUri(addr, 21_000uL)
        assertEquals(Bip321.PaymentRequest(addr, 21_000uL, null), parsed)
    }

    @Test
    fun `parseAddressOrUri accepts URI and keeps its own amount`() {
        val parsed = Bip321.parseAddressOrUri("bitcoin:?tark=$addr&amount=0.0001&label=Hi", 99uL)
        assertEquals(Bip321.PaymentRequest(addr, 10_000uL, "Hi"), parsed)
    }

    @Test
    fun `parseAddressOrUri fills URI amount from fallback`() {
        val parsed = Bip321.parseAddressOrUri("bitcoin:?tark=$addr", 5_000uL)
        assertEquals(Bip321.PaymentRequest(addr, 5_000uL, null), parsed)
    }

    @Test
    fun `parseAddressOrUri trims input and rejects blank`() {
        val parsed = Bip321.parseAddressOrUri("  $addr\n", 1uL)
        assertEquals(addr, parsed.address)
        assertThrows(Bip321.InvalidUriException::class.java) {
            Bip321.parseAddressOrUri("   ", 1uL)
        }
    }
}
