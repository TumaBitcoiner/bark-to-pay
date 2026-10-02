package tech.second.barktopay.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pulsing concentric rings around an "NFC" label — the "hold phones together" visual. */
@Composable
fun NfcPulse(modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "nfcPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
        label = "nfcPulseProgress"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val maxRadius = this.size.minDimension / 2
            for (i in 0 until 3) {
                val p = (progress + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.7f),
                    radius = maxRadius * p,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
        Text("NFC", style = MaterialTheme.typography.headlineSmall, color = color)
    }
}
