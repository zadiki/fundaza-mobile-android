package africa.fundaza.customer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** Seconds left until [deadline], ticking once a second; never negative. */
@Composable
fun rememberSecondsUntil(deadline: Instant?): State<Long?> = produceState<Long?>(initialValue = null, deadline) {
    if (deadline == null) {
        value = null
        return@produceState
    }
    while (true) {
        val left = Duration.between(Instant.now(), deadline).seconds.coerceAtLeast(0)
        value = left
        if (left == 0L) break
        delay(1_000)
    }
}

fun formatMinSec(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
