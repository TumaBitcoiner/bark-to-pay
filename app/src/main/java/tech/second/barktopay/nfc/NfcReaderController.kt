package tech.second.barktopay.nfc

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Activity-scoped wrapper around NFC reader mode for the pay screen. */
class NfcReaderController(private val activity: ComponentActivity) {

    enum class Availability { READY, DISABLED, NO_HARDWARE }

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun availability(): Availability = when {
        adapter == null -> Availability.NO_HARDWARE
        !adapter.isEnabled -> Availability.DISABLED
        else -> Availability.READY
    }

    fun start(onUri: (String) -> Unit, onError: (String) -> Unit) {
        val nfc = adapter ?: run {
            onError("This device has no NFC hardware")
            return
        }
        // Poll NFC-A and NFC-B: some devices route HCE (Type 4 tags) over NFC-B only.
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        nfc.enableReaderMode(
            activity,
            { tag ->
                val result = runCatching { NfcType4Reader.readUriFromTag(tag) }
                activity.runOnUiThread {
                    result
                        .onSuccess(onUri)
                        .onFailure { onError(it.message ?: "Failed to read tag") }
                }
            },
            flags,
            Bundle()
        )
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }
}
