package tech.second.barktopay.ui.pay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.second.barktopay.bip321.Bip321
import tech.second.barktopay.nfc.NfcReaderController
import tech.second.barktopay.ui.common.NfcPulse
import tech.second.barktopay.ui.common.playBark
import tech.second.barktopay.wallet.WalletRepository
import uniffi.bark.validateArkAddress

class PayViewModel : ViewModel() {

    sealed interface State {
        data object Scanning : State
        data object ManualEntry : State
        data class Confirming(val request: Bip321.PaymentRequest, val feeSats: ULong) : State
        data object Sending : State
        data class Sent(val amountSats: ULong) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Scanning)
    val state = _state.asStateFlow()

    /** Transient read problems (tag lost, etc.) shown while staying in scanning state. */
    private val _readHint = MutableStateFlow<String?>(null)
    val readHint = _readHint.asStateFlow()

    /** Validation/fee errors on the manual-entry form. */
    private val _manualError = MutableStateFlow<String?>(null)
    val manualError = _manualError.asStateFlow()

    fun onUriRead(uri: String) {
        if (_state.value !is State.Scanning) return
        viewModelScope.launch {
            try {
                val request = Bip321.parse(uri)
                if (!validateArkAddress(request.address)) {
                    throw Bip321.InvalidUriException("Invalid Ark address in request")
                }
                if (request.amountSats == null || request.amountSats == 0uL) {
                    throw Bip321.InvalidUriException("Payment request has no amount")
                }
                val fee = WalletRepository.estimateArkoorFee(request.amountSats)
                _state.value = State.Confirming(request, fee.feeSats)
            } catch (e: Exception) {
                _readHint.value = e.message ?: "Could not read payment request"
            }
        }
    }

    fun onReadError(message: String) {
        if (_state.value is State.Scanning) _readHint.value = message
    }

    fun clearHint() {
        _readHint.value = null
    }

    fun openManualEntry() {
        if (_state.value !is State.Scanning) return
        _manualError.value = null
        _state.value = State.ManualEntry
    }

    fun backToScanning() {
        if (_state.value !is State.ManualEntry) return
        _state.value = State.Scanning
    }

    /** Pasted address or `bitcoin:` URI + (fallback) amount → validate → fee → Confirming. */
    fun onManualSubmit(input: String, amountSats: ULong?) {
        if (_state.value !is State.ManualEntry) return
        viewModelScope.launch {
            try {
                val request = Bip321.parseAddressOrUri(input, amountSats)
                if (!validateArkAddress(request.address)) {
                    throw Bip321.InvalidUriException("Not a valid Ark address")
                }
                if (request.amountSats == null || request.amountSats == 0uL) {
                    throw Bip321.InvalidUriException("Enter an amount")
                }
                val fee = WalletRepository.estimateArkoorFee(request.amountSats)
                _state.value = State.Confirming(request, fee.feeSats)
            } catch (e: Exception) {
                _manualError.value = e.message ?: "Invalid address"
            }
        }
    }

    fun confirm() {
        val confirming = _state.value as? State.Confirming ?: return
        _state.value = State.Sending
        viewModelScope.launch {
            try {
                WalletRepository.sendArkoor(
                    confirming.request.address,
                    confirming.request.amountSats!!
                )
                _state.value = State.Sent(confirming.request.amountSats)
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Payment failed")
            }
        }
    }

    fun cancel() {
        _state.value = State.Scanning
    }

    fun retry() {
        _state.value = State.Scanning
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen(
    nfcReader: NfcReaderController,
    onBack: () -> Unit,
    vm: PayViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val readHint by vm.readHint.collectAsState()
    val manualError by vm.manualError.collectAsState()
    val context = LocalContext.current

    val hasCameraHardware = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Reader is armed exactly while this screen is shown.
    DisposableEffect(Unit) {
        vm.clearHint()
        nfcReader.start(onUri = vm::onUriRead, onError = vm::onReadError)
        onDispose { nfcReader.stop() }
    }

    // Ask for the camera once per visit to the scanning state.
    LaunchedEffect(state is PayViewModel.State.Scanning) {
        if (state is PayViewModel.State.Scanning && hasCameraHardware && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay") },
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
                is PayViewModel.State.Scanning -> PayScanning(
                    availability = nfcReader.availability(),
                    readHint = readHint,
                    showCamera = hasCameraHardware && hasCameraPermission,
                    onQr = vm::onUriRead,
                    onEnableNfc = {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                    onPasteAddress = vm::openManualEntry,
                    onBack = onBack
                )
                is PayViewModel.State.ManualEntry -> PayManualEntry(
                    error = manualError,
                    onSubmit = vm::onManualSubmit,
                    onBack = vm::backToScanning
                )
                is PayViewModel.State.Sending -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Sending…", style = MaterialTheme.typography.titleLarge)
                }
                is PayViewModel.State.Sent -> PaySuccess(s, onDone = onBack)
                is PayViewModel.State.Error -> PayError(s.message, onRetry = vm::retry, onBack = onBack)
                is PayViewModel.State.Confirming -> Unit // handled by sheet below
            }
        }
    }

    (state as? PayViewModel.State.Confirming)?.let { confirming ->
        ModalBottomSheet(onDismissRequest = vm::cancel) {
            ConfirmPaymentSheet(
                state = confirming,
                onConfirm = vm::confirm,
                onCancel = vm::cancel
            )
        }
    }
}

@Composable
private fun PayScanning(
    availability: NfcReaderController.Availability,
    readHint: String?,
    showCamera: Boolean,
    onQr: (String) -> Unit,
    onEnableNfc: () -> Unit,
    onPasteAddress: () -> Unit,
    onBack: () -> Unit
) {
    if (showCamera) {
        // Camera + NFC are armed simultaneously: whichever delivers a request first wins.
        Text("Ready to pay", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        QrScanner(
            onQr = onQr,
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when (availability) {
                NfcReaderController.Availability.READY ->
                    "Scan a payment QR code or hold phones together"
                NfcReaderController.Availability.DISABLED ->
                    "Scan a payment QR code (NFC is off)"
                NfcReaderController.Availability.NO_HARDWARE ->
                    "Scan a payment QR code"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        readHint?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPasteAddress, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Paste address instead")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Back")
        }
        return
    }

    // NFC-only fallbacks (no camera hardware or permission denied).
    when (availability) {
        NfcReaderController.Availability.NO_HARDWARE -> {
            Text("No NFC hardware", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "This device cannot pay by tap.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        NfcReaderController.Availability.DISABLED -> {
            Text("NFC is off", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Turn on NFC to pay by tap.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onEnableNfc, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Open NFC settings")
            }
        }
        NfcReaderController.Availability.READY -> {
            Text("Hold phones together", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            NfcPulse()
            readHint?.let {
                Spacer(Modifier.height(24.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onPasteAddress, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Paste address instead")
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Back")
    }
}

@Composable
private fun PayManualEntry(
    error: String?,
    onSubmit: (String, ULong?) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var addressText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    // If the pasted text is a URI carrying its own amount, that amount wins and the
    // field becomes read-only; a raw address (or amount-less URI) needs manual input.
    val uriAmount = remember(addressText) {
        runCatching { Bip321.parseAddressOrUri(addressText, null) }.getOrNull()?.amountSats
    }
    val enteredAmount = amountText.toULongOrNull()?.takeIf { it > 0uL }
    val effectiveAmount = uriAmount ?: enteredAmount

    Text("Send to address", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = addressText,
        onValueChange = { addressText = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Ark address or payment link") },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        minLines = 2,
        trailingIcon = {
            IconButton(onClick = { clipboard.getText()?.let { addressText = it.text.trim() } }) {
                Text("Paste", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = uriAmount?.toString() ?: amountText,
        onValueChange = { amountText = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Amount (sats)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = uriAmount == null,
        supportingText = if (uriAmount != null) {
            { Text("Amount is set by the payment link") }
        } else null
    )
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onSubmit(addressText, effectiveAmount) },
        enabled = addressText.isNotBlank() && effectiveAmount != null,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("Continue") }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Back")
    }
}

@Composable
private fun ConfirmPaymentSheet(
    state: PayViewModel.State.Confirming,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val fmt = NumberFormat.getIntegerInstance()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Confirm payment", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "${fmt.format(state.request.amountSats!!.toLong())} sats",
            style = MaterialTheme.typography.displayMedium
        )
        state.request.label?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Fee: ${fmt.format(state.feeSats.toLong())} sats",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Confirm & pay")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PaySuccess(state: PayViewModel.State.Sent, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { playBark(context) }

    Text("Sent!", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "-${NumberFormat.getIntegerInstance().format(state.amountSats.toLong())} sats",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Done") }
}

@Composable
private fun PayError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Text("Payment failed", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Try again") }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Back") }
}
