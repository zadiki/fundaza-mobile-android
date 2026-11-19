package africa.fundaza.customer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Text whose characters roll like an odometer when they change: digits that went up roll up,
 * digits that went down roll down, and characters that stayed put don't move. Made for amounts.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        // Keyed from the right, so "999" → "1,000" rolls the units, not the whole figure.
        val padded = text.reversed()
        for (i in padded.indices.reversed()) {
            val char = padded[i]
            key(i) { AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { if (up) it else -it } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { if (up) -it else it } + fadeOut(tween(160))) using
                        SizeTransform(clip = true)
                },
                label = "roll$i",
            ) { c ->
                Text(c.toString(), style = style, color = color, maxLines = 1, softWrap = false)
            } }
        }
    }
}

/**
 * Fades and lifts a block into place when it first appears; [order] staggers a column of blocks
 * so they arrive one after another rather than all at once.
 */
fun Modifier.reveal(order: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    val lift = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(order * 70L)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * lift
    }
}
