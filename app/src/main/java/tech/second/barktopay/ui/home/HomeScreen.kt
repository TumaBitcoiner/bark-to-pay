package tech.second.barktopay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlinx.coroutines.launch
import tech.second.barktopay.wallet.WalletRepository

@Composable
fun HomeScreen(onReceive: () -> Unit, onPay: () -> Unit, onReset: () -> Unit) {
    val status by WalletRepository.status.collectAsState()
    val balance by WalletRepository.balanceSats.collectAsState()
    val lastError by WalletRepository.lastError.collectAsState()
    val history by WalletRepository.history.collectAsState()
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { WalletRepository.refreshBalance() }
        runCatching { WalletRepository.refreshHistory() }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset wallet?") },
            text = {
                Text("This deletes the wallet and its recovery phrase from this phone. Any funds in it will be lost.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    scope.launch {
                        runCatching { WalletRepository.resetWallet() }
                        onReset()
                    }
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(112.dp))
        Text("Balance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        when (status) {
            WalletRepository.Status.READY -> Text(
                "${balance?.let { NumberFormat.getIntegerInstance().format(it.toLong()) } ?: "…"} sats",
                style = MaterialTheme.typography.displayMedium
            )
            WalletRepository.Status.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    lastError ?: "Wallet error",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { scope.launch { runCatching { WalletRepository.retryOpen() } } },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Try again") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showResetConfirm = true }) {
                    Text("Reset wallet", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> Text("Opening wallet…", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Signet test network · received payments settle in the background",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (status == WalletRepository.Status.READY) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Activity",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            if (history.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No activity yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(history) { movement -> MovementRowView(MovementUiMapper.map(movement)) }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onReceive,
                enabled = status == WalletRepository.Status.READY,
                modifier = Modifier.weight(1f).height(72.dp)
            ) { Text("Receive", style = MaterialTheme.typography.titleMedium) }
            Button(
                onClick = onPay,
                enabled = status == WalletRepository.Status.READY,
                modifier = Modifier.weight(1f).height(72.dp)
            ) { Text("Pay", style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MovementRowView(row: MovementRow) {
    val fmt = NumberFormat.getIntegerInstance()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (row.isIncoming) "+" else "-") + fmt.format(row.amountSats) + " sats",
                style = MaterialTheme.typography.titleMedium,
                color = if (row.isIncoming) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            row.kindLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                row.statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.settled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
