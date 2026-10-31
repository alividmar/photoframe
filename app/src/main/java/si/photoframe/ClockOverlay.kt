package si.photoframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.sin

fun ClockPosition.alignment(): Alignment = when (this) {
    ClockPosition.TOP_LEFT -> Alignment.TopStart
    ClockPosition.TOP_RIGHT -> Alignment.TopEnd
    ClockPosition.CENTER -> Alignment.Center
    ClockPosition.BOTTOM_LEFT -> Alignment.BottomStart
    ClockPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
}

/** Polozaj kot par (vodoravno, navpicno) v razponu -1..1. */
fun ClockPosition.bias(): Pair<Float, Float> = when (this) {
    ClockPosition.TOP_LEFT -> -1f to -1f
    ClockPosition.TOP_RIGHT -> 1f to -1f
    ClockPosition.CENTER -> 0f to 0f
    ClockPosition.BOTTOM_LEFT -> -1f to 1f
    ClockPosition.BOTTOM_RIGHT -> 1f to 1f
}

/** OLED premik: ura se nakljucno odmakne od izbranega polozaja (r je nakljucno stevilo 0..1). */
fun wander(base: Float, r: Float): Float = when {
    base > 0.5f -> base - r * 0.6f
    base < -0.5f -> base + r * 0.6f
    else -> (r * 2f - 1f) * 0.6f
}

/** Cas, ki se osvezi vsako sekundo (ali vsako minuto, ce sekund ne prikazujemo). */
@Composable
fun rememberNow(precise: Boolean): State<LocalDateTime> =
    produceState(initialValue = LocalDateTime.now(), key1 = precise) {
        while (true) {
            value = LocalDateTime.now()
            val period = if (precise) 1000L else 60_000L
            delay(period - (System.currentTimeMillis() % period))
        }
    }

/** Ura (digitalna ali analogna) z barvami in ozadjem po nastavitvah. */
@Composable
fun ClockOverlay(s: AppSettings, modifier: Modifier = Modifier) {
    if (s.clockStyle == ClockStyle.OFF) return
    val now by rememberNow(s.showSeconds)
    val fg = Color(s.clockColor)
    val bg = Color(s.clockBgColor).copy(alpha = s.clockBgAlpha)

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(24.dp))
            .padding(24.dp),
    ) {
        when (s.clockStyle) {
            ClockStyle.DIGITAL -> DigitalClock(now, s, fg)
            ClockStyle.ANALOG -> AnalogClock(now, s, fg)
            ClockStyle.OFF -> Unit
        }
    }
}

@Composable
private fun DigitalClock(now: LocalDateTime, s: AppSettings, fg: Color) {
    val pattern = when {
        s.use24h && s.showSeconds -> "HH:mm:ss"
        s.use24h -> "HH:mm"
        s.showSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    val family = when (s.digitalFont) {
        DigitalFont.SANS -> FontFamily.SansSerif
        DigitalFont.SERIF -> FontFamily.Serif
        DigitalFont.MONO -> FontFamily.Monospace
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern(pattern)),
            color = fg,
            fontSize = (72 * s.clockScale).sp,
            fontFamily = family,
            fontWeight = if (s.digitalFont == DigitalFont.SANS) FontWeight.Light else FontWeight.Normal,
        )
        if (s.showDate) {
            Text(
                text = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                color = fg,
                fontSize = (20 * s.clockScale).sp,
                fontFamily = family,
            )
        }
    }
}

@Composable
private fun AnalogClock(now: LocalDateTime, s: AppSettings, fg: Color) {
    val dim = (220 * s.clockScale).dp
    Canvas(Modifier.size(dim)) {
        val c = center
        val r = size.minDimension / 2f

        // Pomozna funkcija: tocka na krogu. Kot 0 je zgoraj (ura 12), narasca v smeri urinega kazalca.
        fun point(degrees: Double, length: Float): Offset {
            val rad = Math.toRadians(degrees - 90.0)
            return Offset(c.x + cos(rad).toFloat() * length, c.y + sin(rad).toFloat() * length)
        }

        drawCircle(color = fg, radius = r * 0.98f, center = c, style = Stroke(width = r * 0.03f))

        when (s.analogFace) {
            AnalogFace.TICKS -> for (i in 0 until 60) {
                val major = i % 5 == 0
                drawLine(
                    color = fg,
                    start = point(i * 6.0, r * if (major) 0.86f else 0.92f),
                    end = point(i * 6.0, r * 0.96f),
                    strokeWidth = r * if (major) 0.035f else 0.012f,
                    cap = StrokeCap.Round,
                )
            }
            AnalogFace.DOTS -> for (i in 0 until 12) {
                drawCircle(color = fg, radius = r * 0.03f, center = point(i * 30.0, r * 0.88f))
            }
            AnalogFace.NONE -> Unit
        }

        val seconds = if (s.showSeconds) now.second + now.nano / 1_000_000_000.0 else 0.0
        val minutes = now.minute + seconds / 60.0
        val hours = (now.hour % 12) + minutes / 60.0

        drawLine(fg, c, point(hours * 30.0, r * 0.5f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
        drawLine(fg, c, point(minutes * 6.0, r * 0.75f), strokeWidth = r * 0.04f, cap = StrokeCap.Round)
        if (s.showSeconds) {
            drawLine(fg, c, point(seconds * 6.0, r * 0.85f), strokeWidth = r * 0.015f, cap = StrokeCap.Round)
        }
        drawCircle(color = fg, radius = r * 0.05f, center = c)
    }
}
