package africa.fundaza.customer.ui

import africa.fundaza.customer.R
import africa.fundaza.customer.ui.theme.Bricolage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SplashForest = Color(0xFF0E3B2E)
private val SplashMarigold = Color(0xFFF2A93B)

/**
 * The brand screen the app opens on. It starts exactly where the system splash
 * leaves off (same colour, same icon, same size and place), so the hand-over is
 * invisible, then brings in the wordmark while the session and first data load.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val wordmark = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        wordmark.animateTo(1f, tween(durationMillis = 450, delayMillis = 120, easing = FastOutSlowInEasing))
    }
    // A steady turn, so a long wait on the network still feels alive without a spinner.
    // It starts at 0°, so the hand-over from the system splash stays invisible.
    val spin by rememberInfiniteTransition(label = "splash").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "spin",
    )

    Box(modifier.fillMaxSize().background(SplashForest), contentAlignment = Alignment.Center) {
        // 288dp is the size Android draws a background-less splash icon at.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(288.dp).graphicsLayer { rotationZ = spin },
        )
        Text(
            "fundaza",
            color = SplashMarigold,
            fontFamily = Bricolage,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            modifier = Modifier
                .offset(y = 112.dp)
                .graphicsLayer {
                    alpha = wordmark.value
                    translationY = (1f - wordmark.value) * 24.dp.toPx()
                },
        )
    }
}
