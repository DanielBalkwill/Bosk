package app.bosk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.settings.SettingsRepository
import app.bosk.core.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides whether to show onboarding or the main app. null = still loading. */
@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val onboarded: StateFlow<Boolean?> =
        settings.onboardingComplete.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Drives [app.bosk.ui.theme.BoskTheme] from Settings — read at the top of the app tree. */
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
