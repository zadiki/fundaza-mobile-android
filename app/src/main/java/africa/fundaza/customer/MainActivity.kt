package africa.fundaza.customer

import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.ui.AppViewModel
import africa.fundaza.customer.ui.navigation.FundazaApp
import africa.fundaza.customer.ui.theme.FundazaTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { appViewModel.sessionState.value is SessionState.Restoring }
        setContent {
            FundazaTheme {
                FundazaApp(appViewModel)
            }
        }
    }
}
