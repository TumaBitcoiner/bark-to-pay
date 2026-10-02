package tech.second.barktopay.wallet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed encrypted storage for the wallet mnemonic. */
class MnemonicStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(): String? = prefs.getString(KEY_MNEMONIC, null)

    fun put(mnemonic: String) {
        prefs.edit().putString(KEY_MNEMONIC, mnemonic).apply()
    }

    fun exists(): Boolean = get() != null

    fun clear() {
        prefs.edit().remove(KEY_MNEMONIC).apply()
    }

    private companion object {
        const val FILE_NAME = "bark_wallet_secret"
        const val KEY_MNEMONIC = "mnemonic"
    }
}
