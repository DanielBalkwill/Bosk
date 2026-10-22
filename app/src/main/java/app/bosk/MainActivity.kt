package app.bosk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bosk.ui.RootViewModel
import app.bosk.ui.navigation.BoskNavHost
import app.bosk.ui.screens.onboarding.OnboardingScreen
import app.bosk.ui.theme.BoskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12-style splash on every version; hands over to Theme.Bosk (see themes.xml).
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BoskTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val rootViewModel: RootViewModel = hiltViewModel()
                    val onboarded by rootViewModel.onboarded.collectAsStateWithLifecycle()
                    when (onboarded) {
                        null -> Unit // brief loading; window background covers it
                        false -> OnboardingScreen()
                        true -> BoskNavHost()
                    }
                }
            }
        }
    }
}
