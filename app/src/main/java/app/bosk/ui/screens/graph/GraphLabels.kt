package app.bosk.ui.screens.graph

/**
 * Node-label opacity as the graph zooms past the "render distance" threshold. Instead of names
 * popping in abruptly at [minScale], they fade in over a [band] of zoom that ends at the threshold:
 * 0 below the band, 1 at and above [minScale], eased with smoothstep so the fade feels smooth.
 *
 * Pure (no Android/Compose) so it's unit-tested; shared by the vault graph and the flashcard graph.
 */
fun labelFadeAlpha(scale: Float, minScale: Float, band: Float = 0.4f): Float {
    if (band <= 0f) return if (scale >= minScale) 1f else 0f
    val t = ((scale - (minScale - band)) / band).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t) // smoothstep ease-in-out
}
