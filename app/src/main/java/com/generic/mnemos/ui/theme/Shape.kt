package com.generic.mnemos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radii from the design tokens: 12dp cards, 8dp buttons, 24dp overlay popup.
val MnemosShapes = Shapes(
    small = RoundedCornerShape(8.dp),    // buttons
    medium = RoundedCornerShape(12.dp),  // cards
    large = RoundedCornerShape(24.dp),   // overlay popup / bottom sheets
)
