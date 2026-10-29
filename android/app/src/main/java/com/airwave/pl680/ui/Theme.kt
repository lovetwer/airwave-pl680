package com.airwave.pl680.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.airwave.pl680.R

val Mono = FontFamily(Font(R.font.jetbrains_mono))

object C {
    val bg = Color(0xFF0A0B0C)
    val shellTop = Color(0xFF1C1F22)
    val shellMid = Color(0xFF171A1D)
    val shellBot = Color(0xFF131619)
    val red = Color(0xFFE0483C)
    val redText = Color(0xFFFF6A5D)
    val text = Color(0xFFDDE0DB)
    val textSoft = Color(0xFFC2C7C7)
    val textDim = Color(0xFF8D9497)
    val textFaint = Color(0xFF5F6669)
    val textMono = Color(0xFF7B8285)

    val lcdTop = Color(0xFFBDC7B8)
    val lcdMid = Color(0xFFAAB5A4)
    val lcdBot = Color(0xFF97A291)
    val lcdInk = Color(0xFF2B362E)
    val lcdDim = Color(0xFF4B573F)
    val lcdBezel = Color(0xFF0A0C0D)

    val keyTop = Color(0xFF2B3034)
    val keyBot = Color(0xFF191C1F)
    val keyActiveTop = Color(0xFF3A2220)
    val keyActiveBot = Color(0xFF2A1A19)
    val keyEdge = Color(0xFF080A0B)
    val keyText = Color(0xFFC6CBCC)
    val keyTextActive = Color(0xFFFFB3AA)

    val cardTop = Color(0xFF1C1F22)
    val cardBot = Color(0xFF14171A)
    val panelTop = Color(0xFF1F2327)
    val panelBot = Color(0xFF15181B)
    val stroke = Color(0xFF090B0C)
    val hair = Color(0x0BFFFFFF)
    val rowLine = Color(0xFF1F2326)
}

@Composable
fun AirwaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.red,
            background = C.bg,
            surface = C.cardTop,
            onSurface = C.text,
        ),
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default, content = content)
    }
}
