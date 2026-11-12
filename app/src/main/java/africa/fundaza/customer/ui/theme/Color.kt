package africa.fundaza.customer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Forest and paper: the calm of a ledger. Marigold: the moment money moves.
internal val Forest900 = Color(0xFF0B2A21)
internal val Forest800 = Color(0xFF0E3B2E)
internal val Forest700 = Color(0xFF14523F)
internal val Forest500 = Color(0xFF2E7D62)
internal val Forest200 = Color(0xFFA9D3C2)
internal val Forest100 = Color(0xFFD8EDE4)
internal val Paper = Color(0xFFF6F1E7)
internal val PaperDeep = Color(0xFFECE4D3)
internal val Card = Color(0xFFFFFCF6)
internal val Ink = Color(0xFF16201C)
internal val InkSoft = Color(0xFF55605B)
internal val Line = Color(0xFFDCD3C1)
internal val Marigold = Color(0xFFF2A93B)
internal val MarigoldDeep = Color(0xFF9A5B00)
internal val Clay = Color(0xFFB4442E)

internal val NightBg = Color(0xFF0B1512)
internal val NightSurface = Color(0xFF12201B)
internal val NightCard = Color(0xFF172923)
internal val NightLine = Color(0xFF2A3D36)
internal val NightInk = Color(0xFFECE6DA)
internal val NightInkSoft = Color(0xFFA3AEA8)

/** Status colours sit outside Material's scheme: each needs a fill and an ink. */
@Immutable
data class StatusColors(
    val progressBg: Color, val progressFg: Color,
    val successBg: Color, val successFg: Color,
    val problemBg: Color, val problemFg: Color,
    val returnedBg: Color, val returnedFg: Color,
)

internal val LightStatus = StatusColors(
    progressBg = Color(0xFFFCEBCB), progressFg = MarigoldDeep,
    successBg = Forest100, successFg = Forest700,
    problemBg = Color(0xFFF6DAD3), problemFg = Clay,
    returnedBg = PaperDeep, returnedFg = InkSoft,
)

internal val DarkStatus = StatusColors(
    progressBg = Color(0xFF3A2C12), progressFg = Marigold,
    successBg = Color(0xFF173A2E), successFg = Forest200,
    problemBg = Color(0xFF3D1E18), problemFg = Color(0xFFF0A08F),
    returnedBg = Color(0xFF243530), returnedFg = NightInkSoft,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }
