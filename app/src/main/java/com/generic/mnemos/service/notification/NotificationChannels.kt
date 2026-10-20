package com.generic.mnemos.service.notification

/**
 * Notification channel definitions. TODO CP4: create channels in MnemosApplication and
 * build the persistent "Now Playing" (podcast-style) notification with play/pause/skip.
 */
object NotificationChannels {
    const val NOW_PLAYING = "mnemos.now_playing"   // ongoing TTS playback controls
    const val AMBIENT = "mnemos.ambient"           // surfaced insights / review nudges
}
