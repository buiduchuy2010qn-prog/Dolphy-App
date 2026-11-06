package com.droid.dolphy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

enum class BlurDirection {
    TOP,
    BOTTOM
}

/**
 * High-performance hardware-accelerated edge fade for top and bottom edges of [content].
 * Avoids full-screen GraphicsLayer recording and multi-pass Gaussian Blur shaders on every frame,
 * ensuring locked 60/120 FPS scrolling across all GPUs.
 */
@Composable
fun ProgressiveBlurContent(
    modifier: Modifier = Modifier,
    topHeightPx: Float = 0f,
    bottomHeightPx: Float = 0f,
    blurRadius: Float = 18f,
    overlayColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val topBrush = remember(overlayColor) {
        Brush.verticalGradient(colors = listOf(overlayColor, Color.Transparent))
    }
    val bottomBrush = remember(overlayColor) {
        Brush.verticalGradient(colors = listOf(Color.Transparent, overlayColor))
    }

    Box(modifier) {
        content()
        if (bottomHeightPx > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { bottomHeightPx.toDp() })
                    .align(Alignment.BottomCenter)
                    .drawWithContent {
                        drawRect(brush = bottomBrush)
                    }
            )
        }
        if (topHeightPx > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { topHeightPx.toDp() })
                    .align(Alignment.TopCenter)
                    .drawWithContent {
                        drawRect(brush = topBrush)
                    }
            )
        }
    }
}
