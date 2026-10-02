package tech.second.barktopay.nfc

import android.content.Context
import android.nfc.NfcAdapter

/** Stateless NFC availability check for screens that don't own an [NfcReaderController]. */
object NfcAvailability {

    enum class State { READY, DISABLED, NO_HARDWARE }

    fun check(context: Context): State {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return State.NO_HARDWARE
        return if (adapter.isEnabled) State.READY else State.DISABLED
    }
}
