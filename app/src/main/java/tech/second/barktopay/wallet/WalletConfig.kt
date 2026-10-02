package tech.second.barktopay.wallet

import uniffi.bark.Config
import uniffi.bark.Network

/**
 * Hardcoded signet configuration for the testing phase.
 * Deliberately not exposed in the UI. Flip here (and only here) when moving networks.
 */
object WalletConfig {
    const val ARK_SERVER = "https://ark.signet.2nd.dev"
    const val ESPLORA_SERVER = "https://esplora.signet.2nd.dev"
    const val USER_AGENT = "bark-to-pay/0.1.0"
    val NETWORK = Network.SIGNET

    fun barkConfig() = Config(
        serverAddress = ARK_SERVER,
        serverAccessToken = null,
        esploraAddress = ESPLORA_SERVER,
        bitcoindAddress = null,
        bitcoindCookiefile = null,
        bitcoindUser = null,
        bitcoindPass = null,
        vtxoRefreshExpiryThreshold = null,
        vtxoExitMargin = null,
        htlcRecvClaimDelta = null,
        fallbackFeeRate = null,
        roundTxRequiredConfirmations = null,
        daemonSyncIntervalSecs = null,
        offboardRequiredConfirmations = null,
        daemonManualSync = null,
        lightningReceiveClaimRetries = null,
        userAgent = USER_AGENT,
        vtxoKeyGapLimit = null
    )
}
