package com.generic.mnemos.ui.navigation

import androidx.compose.runtime.compositionLocalOf

/**
 * True while the composing page is the pager's current page AND the pager is at rest. Screens
 * gate their *continuous* animations (infinite waveforms, graph pulses) on this so that
 * off-screen neighbour pages — which [androidx.compose.foundation.pager.HorizontalPager] keeps
 * composed via `beyondViewportPageCount` — don't keep invalidating draw every frame, and so the
 * outgoing page stops animating the instant a swipe starts. The Graph additionally gates its
 * whole (heavy) composition on this, which now means "compose once the fling has landed" rather
 * than mid-animation (the arrival jank).
 *
 * Defaults to `true` so screens shown outside the pager (e.g. the note editor) animate normally.
 */
val LocalPageActive = compositionLocalOf { true }
