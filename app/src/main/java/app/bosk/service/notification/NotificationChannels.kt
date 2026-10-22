package app.bosk.service.notification

/**
 * Notification channel definitions. TODO CP4: create channels in BoskApplication and
 * build the persistent "Now Playing" (podcast-style) notification with play/pause/skip.
 */
object NotificationChannels {
    const val NOW_PLAYING = "bosk.now_playing"   // ongoing TTS playback controls
    const val AMBIENT = "bosk.ambient"           // surfaced insights / review nudges
}
