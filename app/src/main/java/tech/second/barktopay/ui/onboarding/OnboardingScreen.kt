package tech.second.barktopay.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.second.barktopay.wallet.WalletRepository

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var restoreMode by remember { mutableStateOf(false) }
    var mnemonicInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("bark-to-pay", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap-to-pay bitcoin over Ark.\nSignet testing network.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        if (busy) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Opening wallet…")
        } else if (!restoreMode) {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { WalletRepository.createWallet() }
                            .onSuccess { onDone() }
                            .onFailure {
                                error = it.message ?: "Failed to create wallet"
                                busy = false
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Create wallet") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { restoreMode = true },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Restore from recovery phrase") }
        } else {
            OutlinedTextField(
                value = mnemonicInput,
                onValueChange = { mnemonicInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Recovery phrase") },
                minLines = 2
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { WalletRepository.restoreWallet(mnemonicInput) }
                            .onSuccess { onDone() }
                            .onFailure {
                                error = it.message ?: "Failed to restore wallet"
                                busy = false
                            }
                    }
                },
                enabled = mnemonicInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Restore") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { restoreMode = false },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Back") }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
