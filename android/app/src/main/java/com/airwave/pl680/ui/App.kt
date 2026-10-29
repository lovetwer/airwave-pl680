package com.airwave.pl680.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airwave.pl680.Catalog
import com.airwave.pl680.RadioApp
import com.airwave.pl680.Station
import com.airwave.pl680.UiState
import kotlinx.coroutines.delay

@Composable
fun AirwaveApp(app: RadioApp) {
    val ui = app.ui
    BackHandler(enabled = app.anyOverlay()) { app.closeOverlays() }
    LaunchedEffect(Unit) {
        while (true) {
            app.tick()
            delay(15000)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg)) {
        val compact = maxHeight < 850.dp
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF070809), RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(0f to C.shellTop, 0.46f to C.shellMid, 1f to C.shellBot)),
        ) {
            TopStrip(ui)
            BezelRow(app, ui, compact)
            DeckCard(app, ui, compact)
            Spacer(Modifier.height(8.dp))
            ListCard(app, ui, Modifier.weight(1f))
            StatusBar(ui)
        }
        if (ui.settingsOpen) SettingsOverlay(app)
        if (ui.filterOpen) FilterOverlay(app)
        if (ui.linkOpen) LinkOverlay(app)
        if (ui.searchOpen) SearchOverlay(app)
    }
}

/* ---------------- 顶部 ---------------- */

@Composable
private fun TopStrip(ui: UiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF101315), Color(0xFF0C0F10), Color(0xFF0A0C0D))))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color(0xFF767D80),
                topLeft = Offset(w * 0.08f, h * 0.36f),
                size = androidx.compose.ui.geometry.Size(w * 0.74f, h * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f),
            )
            drawLine(
                Color(0xFF767D80),
                Offset(w * 0.5f, h * 0.36f),
                Offset(w * 0.68f, h * 0.12f),
                strokeWidth = w * 0.08f,
            )
            drawCircle(Color(0xFF767D80), w * 0.07f, Offset(w * 0.28f, h * 0.6f))
        }
        Spacer(Modifier.width(7.dp))
        Text("AIRWAVE", color = Color(0xFFD9DCD7), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.4.sp)
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .border(1.dp, Color(0xFF5A2A24), RoundedCornerShape(1.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
        ) {
            Text("PL-680", color = Color(0xFFCF5A4E), fontFamily = Mono, fontSize = 6.sp, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (ui.online) Color(0xFF9DC59A) else C.red))
        Spacer(Modifier.width(5.dp))
        Text(if (ui.online) "在线 ONLINE" else "离线 OFFLINE", color = Color(0xFF8A9194), fontFamily = Mono, fontSize = 6.5.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(10.dp))
        Text(ui.clock, color = C.textFaint, fontFamily = Mono, fontSize = 7.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun BezelRow(app: RadioApp, ui: UiState, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 38.dp else 42.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF23272B), Color(0xFF1A1D20), Color(0xFF17191C))))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(if (compact) 62.dp else 72.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFEF5548), Color(0xFFD13A2E), Color(0xFFA92A20))))
                .border(1.dp, Color(0xFF6F1913), RoundedCornerShape(2.dp))
                .clickable(remember { MutableInteractionSource() }, null) { app.togglePlay() },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFD13A2E), Color.White, Color(0xFFD13A2E)))),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("电源", color = C.textDim, fontFamily = Mono, fontSize = 6.sp, letterSpacing = 1.sp)
            Text("POWER", color = C.textFaint, fontFamily = Mono, fontSize = 5.5.sp, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("AIRWAVE", color = Color(0xFFF5F5EF), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Text(
                "PL-680 · PLL SYNTHESIZED RECEIVER",
                color = Color(0xFF848B8E),
                fontFamily = Mono,
                fontSize = 5.2.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

/* ---------------- 机身 ---------------- */

@Composable
private fun DeckCard(app: RadioApp, ui: UiState, compact: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, C.stroke, RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(C.cardTop, C.cardBot)))
            .padding(8.dp),
    ) {
        Speaker(if (compact) 50.dp else 62.dp)
        LcdPanel(ui, compact)
        Keypad(app, ui, compact)
    }
}

@Composable
private fun Speaker(height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF050606), RoundedCornerShape(4.dp)),
    ) {
        Grille(Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    "PL-680",
                    color = Color(0xFFF5F5EF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(1.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFC33A2E), Color(0xFF97271E))))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        "PLL SYNTHESIZED RECEIVER",
                        color = Color(0xFFF4E9E4),
                        fontFamily = Mono,
                        fontSize = 4.6.sp,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "FM STEREO / LW / MW / SW SSB / AIR",
                    color = Color(0xFFA7ADAE),
                    fontFamily = Mono,
                    fontSize = 4.8.sp,
                    letterSpacing = 0.3.sp,
                )
                Text(
                    "AIRWAVE",
                    color = Color(0xFFDFE2DD),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.6.sp,
                )
            }
        }
    }
}

@Composable
private fun LcdPanel(ui: UiState, compact: Boolean) {
    val digitW = if (compact) 30.dp else 33.dp
    val digitH = if (compact) 68.dp else 78.dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Color(0xFF040505), RoundedCornerShape(5.dp))
            .background(C.lcdBezel)
            .padding(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(0f to C.lcdTop, 0.46f to C.lcdMid, 1f to C.lcdBot))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                SignalBars(ui.playing)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .border(1.dp, C.lcdInk.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "STEREO",
                        color = C.lcdInk,
                        fontFamily = Mono,
                        fontSize = 5.4.sp,
                        lineHeight = 6.sp,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    ui.lcdStatus,
                    color = Color(0xFF3F4C43),
                    fontFamily = Mono,
                    fontSize = 6.8.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                ClockDigits(ui.clock)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val d = ui.freqDigits
                SegDigit(d[0], Modifier.width(digitW).height(digitH))
                Spacer(Modifier.width(3.dp))
                SegDigit(d[1], Modifier.width(digitW).height(digitH))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.width(9.dp).height(digitH)) {
                    if (ui.lcdMode == "FM") Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = digitH * 0.10f)
                            .size(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(C.lcdInk),
                    )
                }
                Spacer(Modifier.width(3.dp))
                SegDigit(d[2], Modifier.width(digitW).height(digitH))
                Spacer(Modifier.width(3.dp))
                SegDigit(d[3], Modifier.width(digitW).height(digitH))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(bottom = 4.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        ui.lcdName,
                        color = Color(0xFF253028),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        ui.lcdMeta,
                        color = C.lcdDim,
                        fontFamily = Mono,
                        fontSize = 7.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .border(1.dp, Color(0xFF7D887A), RoundedCornerShape(1.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ui.lcdMode,
                        color = C.lcdInk,
                        fontFamily = Mono,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "FM 87.5–108 · MW 522–1620",
                    color = C.lcdDim,
                    fontFamily = Mono,
                    fontSize = 6.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ClockDigits(clock: String) {
    val d = Catalog.digits4(clock)
    val ink = C.lcdInk.copy(alpha = 0.46f)
    Row(verticalAlignment = Alignment.Bottom) {
        SegDigit(d[0], Modifier.size(9.dp, 20.dp), ink)
        Spacer(Modifier.width(1.dp))
        SegDigit(d[1], Modifier.size(9.dp, 20.dp), ink)
        Box(Modifier.size(5.dp, 20.dp)) {
            Box(Modifier.align(Alignment.Center).offset(y = (-3).dp).size(2.4.dp).clip(RoundedCornerShape(2.dp)).background(ink))
            Box(Modifier.align(Alignment.Center).offset(y = 3.dp).size(2.4.dp).clip(RoundedCornerShape(2.dp)).background(ink))
        }
        SegDigit(d[2], Modifier.size(9.dp, 20.dp), ink)
        Spacer(Modifier.width(1.dp))
        SegDigit(d[3], Modifier.size(9.dp, 20.dp), ink)
    }
}

@Composable
private fun Keypad(app: RadioApp, ui: UiState, compact: Boolean) {
    val capH = if (compact) 32.dp else 36.dp
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyUnit("[音量−]", "−", Modifier.weight(1f), capHeight = capH) { app.adjustVolume(-5) }
            KeyUnit("[音量+]", "+", Modifier.weight(1f), capHeight = capH) { app.adjustVolume(5) }
            KeyUnit("[上一台]", "<<", Modifier.weight(1f), capHeight = capH) { app.prev() }
            KeyUnit("[下一台]", ">>", Modifier.weight(1f), capHeight = capH) { app.next() }
            KeyUnit(
                "[收藏当前台]",
                "★",
                Modifier.weight(1f),
                capHeight = capH,
                active = ui.currentId != null && ui.favs.contains(ui.currentId),
            ) { ui.currentId?.let { app.toggleFav(it) } }
        }
        Spacer(Modifier.height(6.dp))
        KeyCap(
            if (ui.playing) "|| PAUSE" else "> PLAY",
            Modifier.fillMaxWidth(),
            height = if (compact) 36.dp else 40.dp,
            active = ui.playing,
            fontSize = 12.sp,
        ) { app.togglePlay() }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KeyCap("MUTE", Modifier.width(68.dp), 30.dp, active = ui.muted, fontSize = 9.sp) { app.toggleMute() }
            Spacer(Modifier.width(7.dp))
            KeyCap("SET", Modifier.width(68.dp), 30.dp, active = ui.settingsOpen, fontSize = 9.sp) { app.openSettings() }
            Spacer(Modifier.weight(1f))
            Text(
                if (ui.playing) "● ON AIR" else "○ STANDBY",
                color = if (ui.playing) Color(0xFFCF5A4E) else Color(0xFF4C5254),
                fontFamily = Mono,
                fontSize = 6.5.sp,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.width(10.dp))
            Knob(if (ui.muted) 0 else ui.volume, Modifier.size(34.dp))
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VOL", color = C.textFaint, fontFamily = Mono, fontSize = 5.2.sp, letterSpacing = 0.6.sp)
                Text(
                    if (ui.muted) "MUTE" else "${ui.volume}",
                    color = C.textDim,
                    fontFamily = Mono,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

/* ---------------- 列表 ---------------- */

private fun listTitle(nav: Int): String = when (nav) {
    1 -> "我的收藏"
    2 -> "最近播放"
    3 -> "我的直链"
    else -> "电台库"
}

private fun listEn(nav: Int): String = when (nav) {
    1 -> "FAVORITES"
    2 -> "RECENT"
    3 -> "LINKS"
    else -> "MEMORY"
}

@Composable
private fun ListCard(app: RadioApp, ui: UiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(ui.nav, ui.tag, ui.region, ui.query) { listState.scrollToItem(0) }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, C.stroke, RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1D20), Color(0xFF15181B)))),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(listTitle(ui.nav), color = C.text, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                Spacer(Modifier.width(6.dp))
                Text(listEn(ui.nav), color = C.redText, fontFamily = Mono, fontSize = 6.5.sp, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${ui.pool.size} 个",
                    color = C.textMono,
                    fontFamily = Mono,
                    fontSize = 7.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                if (ui.nav == 3) {
                    KeyCap("＋ 直链", Modifier.width(58.dp), 22.dp, fontSize = 7.sp) { app.openLink() }
                    Spacer(Modifier.width(5.dp))
                }
                SearchKey(ui.searchOpen) { app.toggleSearch() }
                Spacer(Modifier.width(5.dp))
                KeyCap("分类 BAND", Modifier.width(72.dp), 22.dp, active = ui.tag.isNotEmpty() || ui.region.isNotEmpty(), fontSize = 7.sp) {
                    app.openFilter()
                }
            }
            NavRow(app, ui)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF24282B)))
            if (ui.pool.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (ui.nav == 3) "还没有直链 · 点右上「＋ 直链」添加" else "没有匹配的电台 · 换个关键词试试", color = C.textFaint, fontSize = 10.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(bottom = 2.dp)) {
                    itemsIndexed(ui.pool, key = { _, s -> s.id }) { i, s ->
                        StationRow(
                            station = s,
                            index = i,
                            current = s.id == ui.currentId,
                            fav = ui.favs.contains(s.id),
                            onPlay = { app.playRow(i) },
                            onFav = { app.toggleFav(s.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchKey(active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(30.dp, 22.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(if (active) C.keyActiveTop else C.keyTop, C.keyBot)))
            .border(1.dp, C.keyEdge, shape)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp)) {
            val col = if (active) C.keyTextActive else C.keyText
            drawCircle(
                color = col,
                radius = size.minDimension * 0.30f,
                center = Offset(size.width * 0.42f, size.height * 0.42f),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawLine(
                color = col,
                start = Offset(size.width * 0.63f, size.height * 0.63f),
                end = Offset(size.width * 0.93f, size.height * 0.93f),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SearchRow(app: RadioApp, ui: UiState, focus: FocusRequester? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF050607), RoundedCornerShape(3.dp))
            .background(Color(0xFF0D0F11))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SCAN", color = C.redText, fontFamily = Mono, fontSize = 6.sp, letterSpacing = 0.6.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (ui.query.isEmpty()) {
                Text("搜索电台 / 城市 / 频段", color = Color(0xFF5B6265), fontSize = 10.sp, maxLines = 1)
            }
            BasicTextField(
                value = ui.query,
                onValueChange = { app.setQuery(it) },
                singleLine = true,
                textStyle = TextStyle(color = Color(0xFFE6E9E6), fontSize = 10.sp),
                cursorBrush = SolidColor(C.red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        }
        if (ui.query.isNotEmpty()) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF23282B))
                    .clickable(remember { MutableInteractionSource() }, null) { app.setQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color(0xFF9AA1A3), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun NavRow(app: RadioApp, ui: UiState) {
    val navs = listOf(
        "中国电台" to ui.catalog.size,
        "我的收藏" to ui.favs.size,
        "最近播放" to ui.recents.size,
        "我的直链" to ui.links.size,
    )
    LazyRow(
        Modifier.fillMaxWidth().height(32.dp).padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        itemsIndexed(navs) { i, (label, n) ->
            FilterChip(label, ui.nav == i, count = "$n", height = 24.dp) { app.pickNav(i) }
        }
    }
}

@Composable
private fun StationRow(
    station: Station,
    index: Int,
    current: Boolean,
    fav: Boolean,
    onPlay: () -> Unit,
    onFav: () -> Unit,
) {
    val bg = if (current) {
        Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF33201E), Color(0xFF1A1D20))))
    } else Modifier
    Column(
        Modifier
            .fillMaxWidth()
            .then(bg)
            .clickable(remember { MutableInteractionSource() }, null) { onPlay() },
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                color = if (current) Color(0xFFC6D1C2) else Color(0xFF5C6366),
                fontFamily = Mono,
                fontSize = 7.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Box(
                Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (current) C.red else Color(0xFF3A3F42)),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    station.name,
                    color = if (current) Color(0xFFF4F5EF) else Color(0xFFE2E5DF),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Row {
                    Text(Catalog.areaOf(station), color = Color(0xFFA08D88), fontSize = 9.sp, maxLines = 1)
                    Text(" · ", color = C.textMono, fontSize = 9.sp)
                    Text(Catalog.tagline(station), color = C.textMono, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(6.dp))
            MiniBadge(
                Catalog.codecOf(station),
                if (station.hls) Color(0xFFD3A08F) else Color(0xFF7D8487),
                border = if (station.hls) Color(0xFF4A2622) else Color(0xFF23282A),
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { onFav() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (fav) "★" else "☆", color = if (fav) C.redText else Color(0xFF6F7679), fontSize = 13.sp)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (current) Color(0xFF3A2624) else C.rowLine),
        )
    }
}

@Composable
private fun StatusBar(ui: UiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(Color(0xFF101214))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("AIRWAVE PL-680", color = Color(0xFFAEB4B3), fontFamily = Mono, fontSize = 6.5.sp, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(6.dp))
        Text("·", color = Color(0xFF6F7679), fontFamily = Mono, fontSize = 6.5.sp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (ui.online) Color(0xFF9DC59A) else C.red))
        Spacer(Modifier.width(5.dp))
        Text("radio-browser.info", color = Color(0xFF6F7679), fontFamily = Mono, fontSize = 6.5.sp)
        Spacer(Modifier.weight(1f))
        Text("本地记忆 · 直连", color = Color(0xFF666D70), fontFamily = Mono, fontSize = 6.5.sp)
    }
}

/* ---------------- 弹层 ---------------- */

@Composable
private fun Scrim(onDismiss: () -> Unit, ime: Boolean = false, content: @Composable () -> Unit) {
    val base = Modifier
        .fillMaxSize()
        .background(Color(0x9E040506))
    Box(
        (if (ime) base.imePadding() else base)
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, Color(0xFF0A0C0D), RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(C.panelTop, C.panelBot)))
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsOverlay(app: RadioApp) {
    val ui = app.ui
    Scrim(app::closeSettings) {
        PanelCard(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置", color = Color(0xFFD9DDDA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("SETTINGS", color = Color(0xFF8A3A33), fontFamily = Mono, fontSize = 6.5.sp, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("播放", color = C.redText, fontFamily = Mono, fontSize = 6.2.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            SetRow("启动后自动播放", "AUTOPLAY ON LAUNCH", ui.settings.autoplay) { app.setOption("autoplay", !ui.settings.autoplay) }
            SetRow("接着上次电台", "RESUME LAST STATION", ui.settings.resume) { app.setOption("resume", !ui.settings.resume) }
            SetRow("后台继续播放", "PLAY IN BACKGROUND", ui.settings.background) { app.setOption("background", !ui.settings.background) }
            Spacer(Modifier.height(12.dp))
            Text("调谐", color = C.redText, fontFamily = Mono, fontSize = 6.2.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            SetRow("随机播放", "SHUFFLE", ui.settings.shuffle) { app.setOption("shuffle", !ui.settings.shuffle) }
            SetRow("失败自动换台", "SKIP DEAD STATIONS", ui.settings.skip) { app.setOption("skip", !ui.settings.skip) }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AIRWAVE PL-680 · 本地记忆", color = C.textFaint, fontFamily = Mono, fontSize = 6.2.sp)
                Spacer(Modifier.weight(1f))
                KeyCap("完成", Modifier.width(74.dp), 30.dp, fontSize = 10.sp, face = androidx.compose.ui.text.font.FontFamily.Default) {
                    app.closeSettings()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterOverlay(app: RadioApp) {
    val ui = app.ui
    val counts = remember(ui.catalog) { Catalog.REGIONS.associateWith { r -> ui.catalog.count { Catalog.matchesRegion(it, r) } } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x9E040506))
            .clickable(remember { MutableInteractionSource() }, null) { app.closeFilter() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .border(1.dp, Color(0xFF0A0C0D), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Brush.verticalGradient(listOf(C.panelTop, C.panelBot)))
                .clickable(remember { MutableInteractionSource() }, null) { }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("分类", "BAND")
                Spacer(Modifier.weight(1f))
                Text(
                    "×",
                    color = C.textDim,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(remember { MutableInteractionSource() }, null) { app.closeFilter() },
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Catalog.TAGS.forEachIndexed { i, (label, key) ->
                    FilterChip(label, ui.tag == key) { app.pickTag(i) }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel("地方", "AREA")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Catalog.REGIONS.forEach { label ->
                    FilterChip(label, ui.region == label, count = "${counts[label] ?: 0}") { app.pickRegion(label) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap("重置", Modifier.width(70.dp), 30.dp, fontSize = 10.sp, face = androidx.compose.ui.text.font.FontFamily.Default) {
                    app.clearFilters()
                }
                Spacer(Modifier.weight(1f))
                KeyCap("完成", Modifier.width(80.dp), 30.dp, fontSize = 10.sp, face = androidx.compose.ui.text.font.FontFamily.Default) {
                    app.closeFilter()
                }
            }
        }
    }
}

@Composable
private fun SearchOverlay(app: RadioApp) {
    val ui = app.ui
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(ui.query) { listState.scrollToItem(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xC2050708))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, Color(0xFF0A0C0D), RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(C.panelTop, C.panelBot)))
                .padding(vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("搜索", "SEARCH")
                Spacer(Modifier.weight(1f))
                KeyCap("取消", Modifier.width(56.dp), 24.dp, fontSize = 9.sp, face = androidx.compose.ui.text.font.FontFamily.Default) {
                    app.closeSearch()
                }
            }
            Spacer(Modifier.height(8.dp))
            SearchRow(app, ui, focus)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${ui.pool.size} 个结果", color = C.textMono, fontFamily = Mono, fontSize = 6.5.sp)
                Spacer(Modifier.weight(1f))
                Text("点击即收听", color = C.textFaint, fontFamily = Mono, fontSize = 6.5.sp)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF24282B)))
            if (ui.pool.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("没有匹配的电台 · 换个关键词试试", color = C.textFaint, fontSize = 10.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(bottom = 4.dp)) {
                    itemsIndexed(ui.pool, key = { _, s -> s.id }) { i, s ->
                        StationRow(
                            station = s,
                            index = i,
                            current = s.id == ui.currentId,
                            fav = ui.favs.contains(s.id),
                            onPlay = {
                                app.playRow(i)
                                app.closeSearch()
                            },
                            onFav = { app.toggleFav(s.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkOverlay(app: RadioApp) {
    val name = remember { androidx.compose.runtime.mutableStateOf("") }
    val url = remember { androidx.compose.runtime.mutableStateOf("") }
    Scrim(app::closeLink, ime = true) {
        PanelCard(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("添加直链电台", color = Color(0xFFD9DDDA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("DIRECT LINK", color = Color(0xFF8A3A33), fontFamily = Mono, fontSize = 6.5.sp, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("支持 http/https 的 mp3 / aac / m3u8 直播地址", color = Color(0xFF6B7275), fontSize = 9.sp)
            Spacer(Modifier.height(10.dp))
            FieldBox(name.value, { name.value = it }, "名称（可留空）", mono = false)
            Spacer(Modifier.height(8.dp))
            FieldBox(url.value, { url.value = it }, "https://example.com/live.m3u8", mono = true)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                KeyCap("取消", Modifier.width(72.dp), 30.dp, fontSize = 10.sp, face = androidx.compose.ui.text.font.FontFamily.Default) { app.closeLink() }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .width(96.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFD5483A), Color(0xFFB8372C))))
                        .border(1.dp, Color(0xFF6D211A), RoundedCornerShape(3.dp))
                        .clickable(remember { MutableInteractionSource() }, null) { app.addLink(name.value, url.value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("添加并播放", color = Color(0xFFFFF4F1), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun FieldBox(value: String, onChange: (String) -> Unit, placeholder: String, mono: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF0B0D0E), RoundedCornerShape(3.dp))
            .background(Color(0xFF0D0F11))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color(0xFF565D60), fontSize = if (mono) 9.sp else 10.sp, fontFamily = if (mono) Mono else null)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFFE6E9E6),
                fontSize = if (mono) 9.sp else 10.sp,
                fontFamily = if (mono) Mono else null,
            ),
            cursorBrush = SolidColor(C.red),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
