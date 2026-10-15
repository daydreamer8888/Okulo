@file:Suppress("MagicNumber") // Stripe width and spacing in preview display units.

package com.example.okulo.camera

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val WHITE_STRIPE_ALPHA = .7f
private const val BLACK_STRIPE_ALPHA = .55f

@Composable
internal fun CameraHighlightOverlay(regions: HighlightRegions?) {
    if (regions == null) return
    Spacer(
        Modifier.fillMaxSize().semantics { contentDescription = "过曝区域" }.drawWithCache {
            val path = Path()
            val scaleX = size.width / regions.width
            val scaleY = size.height / regions.height
            regions.regions.forEach {
                path.addRect(Rect(it.left * scaleX, it.top * scaleY, it.right * scaleX, it.bottom * scaleY))
            }
            val stripeWidth = 3.dp.toPx()
            val spacing = 12.dp.toPx()
            onDrawBehind {
                clipPath(path) {
                    var x = 0f
                    while (x < size.width + size.height + spacing) {
                        drawLine(
                            Color.White.copy(alpha = WHITE_STRIPE_ALPHA),
                            Offset(x, 0f),
                            Offset(x - size.height, size.height),
                            stripeWidth
                        )
                        drawLine(
                            Color.Black.copy(alpha = BLACK_STRIPE_ALPHA),
                            Offset(x + stripeWidth, 0f),
                            Offset(x + stripeWidth - size.height, size.height),
                            stripeWidth
                        )
                        x += spacing
                    }
                }
            }
        }
    )
}
