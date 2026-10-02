package tech.second.barktopay.wallet

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.second.bark.notificationsFlow
import tech.second.barktopay.sync.SyncWorker
import uniffi.bark.FeeEstimate
import uniffi.bark.Movement
import uniffi.bark.Wallet
import uniffi.bark.WalletNotification
import uniffi.bark.WalletOpenArgs
import uniffi.bark.generateMnemonic
import uniffi.bark.validateMnemonic

/**
 * Singleton owner of the Bark wallet. All wallet ops are suspend and run off the main thread.
 *
 * Exposes StateFlows the UI observes; notifications from the wallet drive balance refreshes
 * and the receive screen's "payment arrived" signal.
 */
@SuppressLint("StaticFieldLeak") // holds applicationContext only
object WalletRepository {

    enum class Status { UNINITIALIZED, OPENING, READY, ERROR }

    private const val OPEN_ATTEMPTS = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val openMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var mnemonicStore: MnemonicStore
    private var wallet: Wallet? = null
    private var notificationJob: Job? = null

    private val _status = MutableStateFlow(Status.UNINITIALIZED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _balanceSats = MutableStateFlow<ULong?>(null)
    val balanceSats: StateFlow<ULong?> = _balanceSats.asStateFlow()

    /** Amount of the most recent incoming movement; receive screen consumes it. */
    private val _lastReceivedSats = MutableStateFlow<Long?>(null)
    val lastReceivedSats: StateFlow<Long?> = _lastReceivedSats.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Movement history, newest first. Refreshed alongside the balance. */
    private val _history = MutableStateFlow<List<Movement>>(emptyList())
    val history: StateFlow<List<Movement>> = _history.asStateFlow()

    private val maintenanceRunning = AtomicBoolean(false)

    /** Idempotent — WorkManager may wake the process without any activity having run. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        mnemonicStore = MnemonicStore(appContext)
    }

    fun hasStoredWallet(): Boolean = mnemonicStore.exists()

    /** Opens a fresh wallet. The mnemonic is persisted only after the open succeeds. */
    suspend fun createWallet() {
        val mnemonic = generateMnemonic()
        openWithMnemonic(mnemonic)
        mnemonicStore.put(mnemonic)
    }

    /** Restores a wallet. The mnemonic is persisted only after the open succeeds. */
    suspend fun restoreWallet(mnemonic: String) {
        val normalized = mnemonic.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
        if (!validateMnemonic(normalized)) {
            throw IllegalArgumentException("Invalid recovery phrase")
        }
        openWithMnemonic(normalized)
        mnemonicStore.put(normalized)
    }

    suspend fun openStored(): Boolean {
        val mnemonic = mnemonicStore.get() ?: return false
        openWithMnemonic(mnemonic)
        return true
    }

    /** Retries opening the stored wallet after a failure (Home error state). */
    suspend fun retryOpen() {
        if (!openStored()) throw IllegalStateException("No stored wallet")
    }

    /** Wipes mnemonic + wallet data and returns to UNINITIALIZED. Caller navigates to onboarding. */
    suspend fun resetWallet() {
        openMutex.withLock {
            runCatching { wallet?.close() }
            wallet = null
            notificationJob?.cancel()
            mnemonicStore.clear()
            walletDir().deleteRecursively()
            SyncWorker.cancel(appContext)
            _balanceSats.value = null
            _history.value = emptyList()
            _lastReceivedSats.value = null
            _lastError.value = null
            _status.value = Status.UNINITIALIZED
        }
    }

    /** One-shot sync for the background worker: open if needed, refresh, settle pending arkoor. */
    suspend fun syncOnce() {
        if (wallet == null) {
            if (!openStored()) return
        }
        refreshBalance()
        refreshHistory()
        runCatching { requireWallet().maintenanceRefresh() }
    }

    suspend fun refreshBalance() {
        val w = requireWallet()
        _balanceSats.value = w.balance().spendableSats
    }

    suspend fun refreshHistory() {
        val w = requireWallet()
        _history.value = w.history().sortedByDescending { it.createdAt }
    }

    /**
     * Best-effort settle of arkoor VTXOs into rounds. Fire-and-forget in the repository
     * scope (it waits for a round and can take minutes); concurrent runs are coalesced.
     */
    fun requestMaintenanceRefresh() {
        if (wallet == null || !maintenanceRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                wallet?.maintenanceRefresh()
            } catch (_: Exception) {
                // Settling is retried on the next sync or incoming payment.
            } finally {
                maintenanceRunning.set(false)
            }
        }
    }

    suspend fun newAddress(): String = requireWallet().newAddress()

    suspend fun estimateArkoorFee(amountSats: ULong): FeeEstimate =
        requireWallet().estimateArkoorPaymentFee(amountSats)

    suspend fun sendArkoor(address: String, amountSats: ULong) {
        requireWallet().sendArkoorPayment(address, amountSats)
        refreshBalance()
        refreshHistory()
    }

    fun clearLastReceived() {
        _lastReceivedSats.value = null
    }

    private suspend fun openWithMnemonic(mnemonic: String) {
        openMutex.withLock {
            _status.value = Status.OPENING
            try {
                var lastFailure: Exception? = null
                for (attempt in 1..OPEN_ATTEMPTS) {
                    // Fail fast when the device is clearly offline; retry transient failures otherwise.
                    if (!Connectivity.isOnline(appContext)) {
                        throw IllegalStateException("No internet connection")
                    }
                    try {
                        openOnce(mnemonic)
                        return
                    } catch (e: Exception) {
                        lastFailure = e
                        if (attempt < OPEN_ATTEMPTS) delay(1000L * (1 shl (attempt - 1)))
                    }
                }
                throw lastFailure ?: IllegalStateException("Failed to open wallet")
            } catch (e: Exception) {
                _status.value = Status.ERROR
                _lastError.value = Connectivity.friendlyMessage(appContext, e)
                throw e
            }
        }
    }

    /** Single open attempt. On success the wallet is READY and notifications are flowing. */
    private suspend fun openOnce(mnemonic: String) {
        runCatching { wallet?.close() }
        notificationJob?.cancel()

        val w = Wallet.open(
            network = WalletConfig.NETWORK,
            mnemonicOrSeed = mnemonic,
            config = WalletConfig.barkConfig(),
            args = WalletOpenArgs(
                runDaemon = true,
                datadir = walletDir().apply { mkdirs() }.absolutePath,
                onchain = null,
                createIfNotExists = true,
                createWithoutServer = false,
                skipRecovery = false
            )
        )
        wallet = w
        _status.value = Status.READY
        observeNotifications(w)
        refreshBalance()
        refreshHistory()
        SyncWorker.schedule(appContext)
    }

    private fun observeNotifications(w: Wallet) {
        notificationJob = scope.launch {
            w.notificationsFlow().collect { notification ->
                when (notification) {
                    is WalletNotification.MovementCreated -> {
                        val sats = notification.movement.effectiveBalanceSats
                        if (sats > 0) {
                            _lastReceivedSats.value = sats
                            // New arkoor receipt: schedule settling it into a round.
                            requestMaintenanceRefresh()
                        }
                        refreshBalance()
                        refreshHistory()
                    }
                    is WalletNotification.MovementUpdated -> {
                        refreshBalance()
                        refreshHistory()
                    }
                    WalletNotification.ChannelLagging -> {
                        refreshBalance()
                        refreshHistory()
                    }
                }
            }
        }
    }

    private fun walletDir() = File(appContext.filesDir, "wallet")

    private fun requireWallet(): Wallet =
        wallet ?: throw IllegalStateException("Wallet not open yet")
}
