package com.airwave.pl680.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airwave.pl680.volumeToDegrees
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    active: Boolean = false,
    face: FontFamily = Mono,
    fontSize: TextUnit = 12.sp,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(3.dp)
    val top = if (active) C.keyActiveTop else C.keyTop
    val bottom = if (active) C.keyActiveBot else C.keyBot
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(0f to top, 0.55f to top, 1f to bottom), shape)
            .border(1.dp, C.keyEdge, shape)
            .clickable(src, null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (pressed) Box(Modifier.matchParentSize().background(Color(0x14FFFFFF)))
        Text(
            text = label,
            color = if (active) C.keyTextActive else C.keyText,
            fontFamily = face,
            fontSize = fontSize,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun KeyUnit(
    hint: String,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    capHeight: Dp = 38.dp,
    hintSize: TextUnit = 7.sp,
    face: FontFamily = Mono,
    onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = hint,
            color = C.redText.copy(alpha = 0.92f),
            fontFamily = Mono,
            fontSize = hintSize,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.height(2.dp))
        KeyCap(label, Modifier.fillMaxWidth(), capHeight, active, face, 11.sp, onClick)
    }
}

private val SEG_A = floatArrayOf(9.83f, 0f, 32.17f, 0f, 35.7f, 4.19f, 32.17f, 8.37f, 9.83f, 8.37f, 6.3f, 4.19f)
private val SEG_B = floatArrayOf(39.9f, 6.51f, 42f, 10.75f, 42f, 37.61f, 39.9f, 41.85f, 37.8f, 37.61f, 37.8f, 10.75f)
private val SEG_C = floatArrayOf(39.9f, 51.15f, 42f, 55.39f, 42f, 82.25f, 39.9f, 86.49f, 37.8f, 82.25f, 37.8f, 55.39f)
private val SEG_D = floatArrayOf(9.83f, 84.63f, 32.17f, 84.63f, 35.7f, 88.82f, 32.17f, 93f, 9.83f, 93f, 6.3f, 88.82f)
private val SEG_E = floatArrayOf(2.1f, 51.15f, 4.2f, 55.39f, 4.2f, 82.25f, 2.1f, 86.49f, 0f, 82.25f, 0f, 55.39f)
private val SEG_F = floatArrayOf(2.1f, 6.51f, 4.2f, 10.75f, 4.2f, 37.61f, 2.1f, 41.85f, 0f, 37.61f, 0f, 10.75f)
private val SEG_G = floatArrayOf(9.83f, 42.3f, 32.17f, 42.3f, 35.7f, 46.49f, 32.17f, 50.67f, 9.83f, 50.67f, 6.3f, 46.49f)
private val SEGMENTS = listOf(SEG_A, SEG_B, SEG_C, SEG_D, SEG_E, SEG_F, SEG_G)

private fun segOn(d: Int): BooleanArray = booleanArrayOf(
    d != 1 && d != 4,
    d != 5 && d != 6,
    d != 2,
    d != 1 && d != 4 && d != 7,
    d == 0 || d == 2 || d == 6 || d == 8,
    d == 0 || d == 4 || d == 5 || d == 6 || d == 8 || d == 9,
    d == 2 || d == 3 || d == 4 || d == 5 || d == 6 || d == 8 || d == 9,
)

@Composable
fun SegDigit(
    digit: Int,
    modifier: Modifier = Modifier,
    ink: Color = C.lcdInk,
    ghost: Boolean = true,
) {
    Canvas(modifier) {
        val sx = size.width / 42f
        val sy = size.height / 93f
        val on = segOn(digit.coerceIn(0, 9))
        SEGMENTS.forEachIndexed { i, pts ->
            val p = Path()
            p.moveTo(pts[0] * sx, pts[1] * sy)
            var k = 2
            while (k < pts.size) {
                p.lineTo(pts[k] * sx, pts[k + 1] * sy)
                k += 2
            }
            p.close()
            val col = if (on[i]) ink else if (ghost) ink.copy(alpha = 0.10f) else Color.Transparent
            if (on[i] || ghost) drawPath(p, col)
        }
    }
}

@Composable
fun Grille(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1D20), Color(0xFF15181A), Color(0xFF111315))))
        val pitch = 7.0.dp.toPx()
        val r = 1.5.dp.toPx()
        var y = pitch * 0.5f
        while (y < size.height) {
            var x = pitch * 0.5f
            while (x < size.width) {
                drawCircle(Color(0x0CFFFFFF), r * 1.7f, Offset(x, y))
                drawCircle(Color(0xFF070909), r, Offset(x, y))
                x += pitch
            }
            y += pitch
        }
    }
}

@Composable
fun Knob(volume: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Color(0xFF15181A), r)
        drawCircle(Color(0xFF24282B), r * 0.95f)
        drawCircle(Color(0xFF0D0F11), r * 0.90f)
        val ang = volumeToDegrees(volume)
        val steps = 44
        for (i in 0..steps) {
            val deg = -132f + i * (264f / steps)
            val rad = Math.toRadians((deg - 90.0))
            val lit = deg <= ang + 0.01f
            drawLine(
                color = if (lit) Color(0xFFA7AFB2) else Color(0xFF383E42),
                start = Offset(c.x + cos(rad).toFloat() * r * 0.74f, c.y + sin(rad).toFloat() * r * 0.74f),
                end = Offset(c.x + cos(rad).toFloat() * r * 0.87f, c.y + sin(rad).toFloat() * r * 0.87f),
                strokeWidth = 1.1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF4C5357), Color(0xFF2B3034), Color(0xFF181B1D)),
                center = Offset(c.x - r * 0.22f, c.y - r * 0.26f),
                radius = r * 1.05f,
            ),
            r * 0.66f,
        )
        val pr = Math.toRadians((ang - 90.0))
        drawLine(
            Color(0xFFF1F2EC),
            Offset(c.x, c.y),
            Offset(c.x + cos(pr).toFloat() * r * 0.48f, c.y + sin(pr).toFloat() * r * 0.48f),
            2.dp.toPx(),
            StrokeCap.Round,
        )
        drawCircle(Color(0xFFF1F2EC), r * 0.05f)
    }
}

@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (on) 19.dp else 2.dp, tween(110), label = "knob")
    val bg by animateColorAsState(if (on) Color(0xFF2C1816) else Color(0xFF1C2023), tween(110), label = "bg")
    Box(
        modifier
            .size(39.dp, 21.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .border(1.dp, if (on) Color(0xFF4A2622) else Color(0xFF0E1113), RoundedCornerShape(11.dp)),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(15.dp)
                .clip(CircleShape)
                .background(if (on) C.red else Color(0xFF5D6467)),
        )
    }
}

@Composable
fun SetRow(title: String, en: String, on: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(47.dp)
                .clickable(
                    remember { MutableInteractionSource() },
                    null,
                ) { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = if (on) Color(0xFFE8EBE7) else C.textSoft, fontSize = 12.sp)
                Text(en, color = C.textFaint, fontFamily = Mono, fontSize = 6.5.sp, letterSpacing = 1.sp)
            }
            Toggle(on)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.hair))
    }
}

@Composable
fun SectionLabel(cn: String, en: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).background(C.red))
        Spacer(Modifier.width(6.dp))
        Text(cn, color = Color(0xFFC4C9C9), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        Spacer(Modifier.width(6.dp))
        Text(en, color = C.redText.copy(alpha = 0.9f), fontFamily = Mono, fontSize = 6.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun FilterChip(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    count: String? = null,
    height: Dp = 26.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(
                if (on) Brush.verticalGradient(listOf(Color(0xFF4A2622), Color(0xFF2A1817)))
                else Brush.verticalGradient(listOf(Color(0xFF23272A), Color(0xFF1A1D20))),
                shape,
            )
            .border(1.dp, if (on) Color(0xFF7A3A32) else Color(0xFF0A0C0D), shape)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (on) Color(0xFFFFB0A7) else Color(0xFFA9AFB0),
                fontSize = 10.5.sp,
                maxLines = 1,
            )
            if (count != null) {
                Spacer(Modifier.width(5.dp))
                Text(count, color = if (on) Color(0xFFE08A7E) else C.textFaint, fontFamily = Mono, fontSize = 7.5.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun MiniBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 6.5.sp,
    border: Color = color.copy(alpha = 0.55f),
) {
    Box(
        modifier
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color,
            fontFamily = Mono,
            fontSize = size,
            lineHeight = size * 1.25f,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun SignalBars(playing: Boolean, modifier: Modifier = Modifier) {
    val heights = listOf(0.30f, 0.48f, 0.66f, 0.84f, 1f)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(2.4.dp)
                    .height(14.dp * h)
                    .background(if (playing) C.lcdInk else Color(0xFF8D9A88)),
            )
            if (i != heights.lastIndex) Spacer(Modifier.width(2.dp))
        }
    }
}
