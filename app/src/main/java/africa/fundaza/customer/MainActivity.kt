package africa.fundaza.customer

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
        // The system splash hands over on the first frame to the in-app SplashScreen,
        // which looks identical and stays up while the session and home screen load.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FundazaTheme {
                FundazaApp(appViewModel)
            }
        }
    }
}
