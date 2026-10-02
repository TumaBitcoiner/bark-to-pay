package tech.second.barktopay.bip321

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Minimal BIP 321 builder/parser for Ark payment URIs.
 *
 * Format: `bitcoin:?<hrp>=<arkAddress>&amount=<BTC-decimal>&label=<urlencoded>`
 * where <hrp> is the address's bech32m HRP (`ark` mainnet, `tark` on signet).
 *
 * Pure Kotlin/JVM — unit-tested without Android.
 */
object Bip321 {

    data class PaymentRequest(
        val address: String,
        val amountSats: ULong?,
        val label: String?
    )

    class InvalidUriException(message: String) : Exception(message)

    /** Address HRPs we treat as the Ark address parameter. */
    private val ADDRESS_KEYS = setOf("ark", "tark")

    fun build(address: String, amountSats: ULong?, label: String?): String {
        val hrp = hrpOf(address)
        val params = mutableListOf("$hrp=$address")
        if (amountSats != null) {
            params += "amount=${satsToBtc(amountSats)}"
        }
        if (!label.isNullOrBlank()) {
            params += "label=${percentEncode(label)}"
        }
        return "bitcoin:?" + params.joinToString("&")
    }

    fun parse(uri: String): PaymentRequest {
        val scheme = uri.substringBefore(':', "")
        if (!scheme.equals("bitcoin", ignoreCase = true)) {
            throw InvalidUriException("Not a bitcoin URI")
        }
        val rest = uri.substringAfter(':', "")
        if (!rest.startsWith("?")) {
            throw InvalidUriException("Unsupported legacy address format (expected BIP 321 query URI)")
        }
        val query = rest.removePrefix("?")
        if (query.isBlank()) {
            throw InvalidUriException("No payment parameters")
        }

        var address: String? = null
        var amountSats: ULong? = null
        var label: String? = null

        for (pair in query.split('&')) {
            if (pair.isBlank()) continue
            val key = percentDecode(pair.substringBefore('='))
            val value = percentDecode(pair.substringAfter('=', ""))
            when (val lowerKey = key.lowercase()) {
                "amount" -> amountSats = btcToSats(value)
                "label" -> label = value
                in ADDRESS_KEYS -> {
                    if (address != null) throw InvalidUriException("Duplicate address parameter")
                    address = value
                }
                else -> {
                    if (lowerKey.startsWith("req-")) {
                        throw InvalidUriException("Unsupported required parameter: $key")
                    }
                    // Unknown optional parameters are ignored (BIP 321 forward compatibility).
                }
            }
        }

        return PaymentRequest(
            address = address ?: throw InvalidUriException("Missing Ark address parameter"),
            amountSats = amountSats,
            label = label
        )
    }

    /**
     * Accepts either a full `bitcoin:` URI or a raw Ark address (`tark1…` / `ark1…`).
     * For a URI without an amount, [fallbackAmountSats] is used instead.
     */
    fun parseAddressOrUri(input: String, fallbackAmountSats: ULong?): PaymentRequest {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) throw InvalidUriException("Nothing to send to")
        return if (trimmed.startsWith("bitcoin:", ignoreCase = true)) {
            val parsed = parse(trimmed)
            parsed.copy(amountSats = parsed.amountSats ?: fallbackAmountSats)
        } else {
            PaymentRequest(address = trimmed, amountSats = fallbackAmountSats, label = null)
        }
    }

    /** bech32m HRP = everything before the last '1' separator. */
    fun hrpOf(address: String): String {
        val hrp = address.substringBeforeLast('1', "").lowercase()
        if (hrp.isBlank()) throw InvalidUriException("Invalid Ark address")
        return hrp
    }

    internal fun satsToBtc(sats: ULong): String {
        val whole = sats / 100_000_000uL
        val frac = (sats % 100_000_000uL).toString().padStart(8, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
    }

    internal fun btcToSats(btc: String): ULong {
        val trimmed = btc.trim()
        if (!trimmed.matches(Regex("\\d+(\\.\\d{1,8})?"))) {
            throw InvalidUriException("Invalid amount: $btc")
        }
        val parts = trimmed.split('.')
        val whole = parts[0].toULong()
        val frac = if (parts.size > 1) parts[1].padEnd(8, '0') else "00000000"
        return whole * 100_000_000uL + frac.toULong()
    }

    private fun percentEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun percentDecode(value: String): String =
        URLDecoder.decode(value, Charsets.UTF_8)
}
