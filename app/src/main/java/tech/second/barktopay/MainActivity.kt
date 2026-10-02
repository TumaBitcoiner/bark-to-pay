package tech.second.barktopay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tech.second.barktopay.nfc.NfcReaderController
import tech.second.barktopay.ui.home.HomeScreen
import tech.second.barktopay.ui.onboarding.OnboardingScreen
import tech.second.barktopay.ui.pay.PayScreen
import tech.second.barktopay.ui.receive.ReceiveScreen
import tech.second.barktopay.ui.theme.BarkToPayTheme
import tech.second.barktopay.wallet.WalletRepository

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val RECEIVE = "receive"
    const val PAY = "pay"
}

class MainActivity : ComponentActivity() {

    private lateinit var nfcReader: NfcReaderController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WalletRepository.init(applicationContext)
        nfcReader = NfcReaderController(this)

        setContent {
            BarkToPayTheme {
                val navController = rememberNavController()
                val hasWallet = WalletRepository.hasStoredWallet()

                // Stored wallet → open it in the background while Home shows "Opening wallet…".
                LaunchedEffect(Unit) {
                    if (hasWallet) runCatching { WalletRepository.openStored() }
                }

                NavHost(
                    navController = navController,
                    startDestination = if (hasWallet) Routes.HOME else Routes.ONBOARDING
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onDone = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        })
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onReceive = { navController.navigate(Routes.RECEIVE) },
                            onPay = { navController.navigate(Routes.PAY) },
                            onReset = {
                                navController.navigate(Routes.ONBOARDING) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.RECEIVE) {
                        ReceiveScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.PAY) {
                        PayScreen(
                            nfcReader = nfcReader,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
