package com.thingsapart.langtutor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
// Import the correct color names based on Color.kt, using aliases for consistency
import com.thingsapart.langtutor.ui.theme.PastelRed
import com.thingsapart.langtutor.ui.theme.PromptPastelYellow as PastelYellow
import com.thingsapart.langtutor.ui.theme.PromptPastelGreen as PastelGreen
import com.thingsapart.langtutor.ui.theme.PromptPastelBlue as PastelBlue
import com.thingsapart.langtutor.ui.theme.PromptPastelPurple as PastelPurple

@Composable
fun MetallicPanelGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Define color stops for the gradient to create panels and sharp edges
    // This is an example configuration and will likely need tweaking for the desired effect.
    // The idea is to have gradual changes, then a stark change.
    // A "stark" change can be simulated by having two color stops at nearly the same position.
    val colorStops = arrayOf(
        // Panel 1: Gradual PastelRed to PastelYellow
        0.0f to PastelRed.copy(alpha = 0.8f),
        0.19f to PastelYellow.copy(alpha = 0.7f),
        // Edge 1: Stark transition to PastelGreen
        0.2f to PastelGreen.copy(alpha = 0.85f),
        // Panel 2: Gradual PastelGreen to PastelBlue
        0.21f to PastelGreen.copy(alpha = 0.8f),
        0.39f to PastelBlue.copy(alpha = 0.7f),
        // Edge 2: Stark transition to PastelPurple
        0.4f to PastelPurple.copy(alpha = 0.85f),
        // Panel 3: Gradual PastelPurple to a lighter PastelRed
        0.41f to PastelPurple.copy(alpha = 0.8f),
        0.59f to PastelRed.copy(alpha = 0.6f),
        // Edge 3: Stark transition back to PastelYellow variant
        0.6f to PastelYellow.copy(alpha = 0.9f),
        // Panel 4: Gradual PastelYellow to a lighter PastelGreen
        0.61f to PastelYellow.copy(alpha = 0.8f),
        0.79f to PastelGreen.copy(alpha = 0.65f),
        // Edge 4: Stark transition to a lighter PastelBlue
        0.8f to PastelBlue.copy(alpha = 0.88f),
        // Panel 5: Gradual PastelBlue to end color
        0.81f to PastelBlue.copy(alpha = 0.8f),
        1.0f to PastelPurple.copy(alpha = 0.7f)
    )

    // Using a large angle linear gradient to give a sense of broad lighting
    val metallicBrush = Brush.linearGradient(
        colorStops = colorStops,
        // angle = 45f, // Could use angle if not using start/end x/y
        // start = Offset(0f, Float.POSITIVE_INFINITY), // Example: diagonal
        // end = Offset(Float.POSITIVE_INFINITY, 0f)
    )
    // For more complex "convex" shapes, one might need multiple overlaid gradients
    // or a custom DrawModifier. This is a simplified interpretation for now.

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = metallicBrush)
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun MetallicPanelGradientBackgroundPreview() {
    LangTutorAppTheme {
        MetallicPanelGradientBackground {
            // You can place preview content here if needed
            Box(modifier = Modifier.fillMaxSize()) // Ensures gradient is visible
        }
    }
}

// Note: The exact appearance will depend heavily on the chosen colors and stop positions.
// The "metallic shape with large convex surfaces" might be hard to achieve with a single Brush
// and might require more advanced drawing or layering of multiple brushes/shapes.
// This implementation provides a starting point for the "panels with sharp edges" idea.
// Alpha values are added to pastel colors for a slightly more subtle effect.
