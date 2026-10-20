package com.generic.mnemos

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
import com.generic.mnemos.ui.RootViewModel
import com.generic.mnemos.ui.navigation.MnemosNavHost
import com.generic.mnemos.ui.screens.onboarding.OnboardingScreen
import com.generic.mnemos.ui.theme.MnemosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12-style splash on every version; hands over to Theme.Mnemos (see themes.xml).
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MnemosTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val rootViewModel: RootViewModel = hiltViewModel()
                    val onboarded by rootViewModel.onboarded.collectAsStateWithLifecycle()
                    when (onboarded) {
                        null -> Unit // brief loading; window background covers it
                        false -> OnboardingScreen()
                        true -> MnemosNavHost()
                    }
                }
            }
        }
    }
}
