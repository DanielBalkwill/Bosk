package com.generic.mnemos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.settings.SettingsRepository
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
}
