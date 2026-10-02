package tech.second.barktopay.ui.receive

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.second.barktopay.bip321.Bip321
import tech.second.barktopay.nfc.HcePayloadStore
import tech.second.barktopay.nfc.NfcAvailability
import tech.second.barktopay.ui.common.NfcPulse
import tech.second.barktopay.ui.common.playBark
import tech.second.barktopay.ui.common.rememberQrBitmap
import tech.second.barktopay.wallet.WalletRepository

class ReceiveViewModel : ViewModel() {

    sealed interface State {
        data object Editing : State
        data class Active(val uri: String, val amountSats: ULong, val label: String?) : State
        data class Address(val uri: String, val address: String, val amountSats: ULong?, val label: String?) : State
        data class Received(val amountSats: Long) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Editing)
    val state = _state.asStateFlow()

    private var watchJob: Job? = null
    private var pulseJob: Job? = null

    /** Tap-to-receive: publishes a BIP 321 URI over NFC (HCE) + shows it as QR. */
    fun arm(context: Context, amountSats: ULong, label: String?) {
        viewModelScope.launch {
            try {
                val address = WalletRepository.newAddress()
                val uri = Bip321.build(address, amountSats, label)
                HcePayloadStore.saveCurrentUri(context.applicationContext, uri)
                _state.value = State.Active(uri, amountSats, label)
                watchIncoming()
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Could not create payment request")
            }
        }
    }

    /** Plain-address receive: no NFC, just a QR + copyable address (faucet / any sender). */
    fun showAddress(amountSats: ULong?, label: String?) {
        viewModelScope.launch {
            try {
                val address = WalletRepository.newAddress()
                val uri = Bip321.build(address, amountSats, label)
                _state.value = State.Address(uri, address, amountSats, label)
                watchIncoming()
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Could not create address")
            }
        }
    }

    fun backToEditing() {
        _state.value = State.Editing
        watchJob?.cancel()
        pulseJob?.cancel()
    }

    private fun watchIncoming() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            WalletRepository.lastReceivedSats.collect { sats ->
                val current = _state.value
                if (sats != null && (current is State.Active || current is State.Address)) {
                    _state.value = State.Received(sats)
                    WalletRepository.clearLastReceived()
                }
            }
        }
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            // Forces a mailbox pull every 2s while armed, so an incoming payment is
            // detected in ~2-4s instead of up to the daemon's 60s sync interval.
            while (_state.value is State.Active || _state.value is State.Address) {
                runCatching { WalletRepository.sync() }
                delay(2_000)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(onBack: () -> Unit, vm: ReceiveViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Stop serving the payment request as soon as we leave this screen.
    DisposableEffect(Unit) {
        onDispose { HcePayloadStore.clear(context.applicationContext) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = state) {
                is ReceiveViewModel.State.Editing -> ReceiveForm(
                    onReady = { sats, label -> vm.arm(context, sats, label) },
                    onShowAddress = { sats, label -> vm.showAddress(sats, label) },
                    onBack = onBack
                )
                is ReceiveViewModel.State.Active -> ReceiveActive(s, onCancel = {
                    HcePayloadStore.clear(context.applicationContext)
                    vm.backToEditing()
                })
                is ReceiveViewModel.State.Address -> ReceiveAddress(s, onCancel = vm::backToEditing)
                is ReceiveViewModel.State.Received -> ReceiveSuccess(s, onDone = onBack)
                is ReceiveViewModel.State.Error -> ReceiveError(s.message, onRetry = vm::backToEditing)
            }
        }
    }
}

@Composable
private fun ReceiveForm(
    onReady: (ULong, String?) -> Unit,
    onShowAddress: (ULong?, String?) -> Unit,
    onBack: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var labelText by remember { mutableStateOf("") }
    val amountSats = amountText.toULongOrNull()?.takeIf { it > 0uL }
    val label = labelText.ifBlank { null }

    Text("How much do you want to receive?", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Amount (sats)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = labelText,
        onValueChange = { labelText = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Label (optional)") },
        singleLine = true
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onReady(amountSats!!, label) },
        enabled = amountSats != null,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("Ready for tap") }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { onShowAddress(amountSats, label) },
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("Show address instead") }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Back")
    }
}

@Composable
private fun ReceiveActive(state: ReceiveViewModel.State.Active, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val qr = rememberQrBitmap(state.uri)

    // HCE serves the tap, so warn if this device can't be tapped right now.
    // Re-checked on resume so toggling NFC in settings updates the hint.
    var nfcState by remember { mutableStateOf(NfcAvailability.check(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nfcState = NfcAvailability.check(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text("Hold phones together", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "${NumberFormat.getIntegerInstance().format(state.amountSats.toLong())} sats" +
            (state.label?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    if (nfcState == NfcAvailability.State.READY) {
        NfcPulse()
        Spacer(Modifier.height(24.dp))
    }
    Image(
        bitmap = qr,
        contentDescription = "Payment request QR code",
        modifier = Modifier.size(200.dp)
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "…or let the payer scan this code",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    when (nfcState) {
        NfcAvailability.State.DISABLED -> {
            Spacer(Modifier.height(16.dp))
            Text(
                "NFC is off — the other phone can't tap you.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Open NFC settings") }
        }
        NfcAvailability.State.NO_HARDWARE -> {
            Spacer(Modifier.height(16.dp))
            Text(
                "This device can't be tapped — use the QR code.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
        NfcAvailability.State.READY -> Unit
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Cancel")
    }
}

@Composable
private fun ReceiveAddress(state: ReceiveViewModel.State.Address, onCancel: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val qr = rememberQrBitmap(state.uri)
    var copied by remember { mutableStateOf(false) }

    Text("Your Ark address", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        if (state.amountSats != null)
            "${NumberFormat.getIntegerInstance().format(state.amountSats.toLong())} sats" +
                (state.label?.let { " · $it" } ?: "")
        else "Any amount",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    Image(
        bitmap = qr,
        contentDescription = "Ark address QR code",
        modifier = Modifier.size(240.dp)
    )
    Spacer(Modifier.height(16.dp))
    SelectionContainer {
        Text(
            state.address,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(state.address))
                copied = true
            },
            modifier = Modifier.weight(1f).height(56.dp)
        ) { Text(if (copied) "Copied!" else "Copy address") }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, state.address)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            modifier = Modifier.weight(1f).height(56.dp)
        ) { Text("Share") }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Paste this address into the faucet or the sender's wallet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Cancel")
    }
}

@Composable
private fun ReceiveSuccess(state: ReceiveViewModel.State.Received, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { playBark(context) }

    Text("Received!", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "+${NumberFormat.getIntegerInstance().format(state.amountSats)} sats",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Settling in the background.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Done") }
}

@Composable
private fun ReceiveError(message: String, onRetry: () -> Unit) {
    Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Try again") }
}
