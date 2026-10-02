package tech.second.barktopay.nfc

import android.content.Context

object HcePayloadStore {
    fun saveCurrentUri(context: Context, uri: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri)
            .apply()
    }

    fun loadCurrentUri(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URI, DEFAULT_URI)
            ?: DEFAULT_URI
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .apply()
    }

    private const val PREFS_NAME = "hce_payload"
    private const val KEY_URI = "current_uri"
    private const val DEFAULT_URI = "bitcoin:?tark=tark1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq0"
}
