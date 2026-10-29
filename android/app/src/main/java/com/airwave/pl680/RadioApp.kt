package com.airwave.pl680

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class UiState(
    val catalog: List<Station> = emptyList(),
    val pool: List<Station> = emptyList(),
    val favs: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
    val links: List<LinkRec> = emptyList(),
    val nav: Int = 0,
    val tag: String = "",
    val region: String = "",
    val query: String = "",
    val current: Station? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val paused: Boolean = false,
    val muted: Boolean = false,
    val volume: Int = 70,
    val settings: Settings = Settings(),
    val settingsOpen: Boolean = false,
    val filterOpen: Boolean = false,
    val searchOpen: Boolean = false,
    val linkOpen: Boolean = false,
    val lcdStatus: String = "待机 STANDBY",
    val lcdMode: String = "FM",
    val clock: String = "--:--",
    val online: Boolean = true,
) {
    val lcdName: String get() = current?.name ?: "还没开始收听"
    val lcdMeta: String
        get() = current?.let { st ->
            val area = Catalog.areaOf(st)
            when {
                st.hls -> "$area · HLS 流 · 网络调谐"
                st.bitrate > 0 -> "$area · ${st.bitrate}kbps · 网络调谐"
                else -> "$area · 网络调谐"
            }
        } ?: "FM 87.5–108 MHz · 网络调谐"
    val freqDigits: IntArray get() = Catalog.digits4(current?.let { Catalog.freqOf(it).first } ?: "0")
    val currentId: String? get() = current?.id
}

class RadioApp(private val ctx: Context) {
    private val store = Store(ctx)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connecting = false
    private var pending: Station? = null
    private var playedId: String? = null
    private var skipGuard = false
    private var bufferWatch: Runnable? = null
    private val main = Handler(Looper.getMainLooper())

    var ui by mutableStateOf(UiState())
        private set

    var onFirstPlay: (() -> Unit)? = null

    private var startAutoPlay = false
    private var firstPlay = true

    init {
        val saved = store.load()
        val catalog = Catalog.load(ctx)
        val current = saved.lastId?.let { id ->
            catalog.firstOrNull { it.id == id } ?: saved.links.firstOrNull { "link:${it.url}" == id }
                ?.let { Catalog.linkStation(it) }
        }.takeIf { saved.settings.resume }
        ui = UiState(
            catalog = catalog,
            favs = saved.favs.toSet(),
            recents = saved.recents,
            links = saved.links,
            volume = saved.volume,
            settings = saved.settings,
            current = current,
            lcdMode = current?.let { Catalog.freqOf(it).second } ?: "FM",
            clock = nowText(),
        ).let { s -> s.copy(pool = poolFor(s)) }
        startAutoPlay = saved.settings.autoplay && current != null
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            connecting = false
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                applyVolume()
                val queued = pending
                pending = null
                when {
                    queued != null -> playNow(queued)
                    startAutoPlay -> {
                        startAutoPlay = false
                        ui.current?.let { playNow(it) }
                    }
                }
            } catch (e: Exception) {
                ui = ui.copy(lcdStatus = "播放服务不可用")
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        cancelBufferWatch()
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ui = ui.copy(
                playing = isPlaying,
                paused = !isPlaying && playedId != null && !ui.loading,
                lcdStatus = when {
                    isPlaying -> "正在收听 ON AIR"
                    ui.loading -> "正在连接…"
                    ui.current == null -> "待机 STANDBY"
                    else -> "已暂停 PAUSED"
                },
            )
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    ui = ui.copy(loading = true, lcdStatus = "正在连接…")
                    armBufferWatch()
                }
                Player.STATE_READY -> {
                    cancelBufferWatch()
                    ui = ui.copy(loading = false)
                    skipGuard = false
                }
                Player.STATE_ENDED -> {
                    cancelBufferWatch()
                    ui = ui.copy(loading = false, playing = false, lcdStatus = "直播中断")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            cancelBufferWatch()
            ui = ui.copy(loading = false, playing = false, lcdStatus = "信号中断 NO SIGNAL")
            autoSkip()
        }
    }

    /** 死流常见表现是无限 BUFFERING，15 秒没有进展就当作信号中断。 */
    private fun armBufferWatch() {
        cancelBufferWatch()
        val r = Runnable {
            bufferWatch = null
            if (!ui.loading) return@Runnable
            ui = ui.copy(loading = false, playing = false, lcdStatus = "信号中断 NO SIGNAL")
            autoSkip()
        }
        bufferWatch = r
        main.postDelayed(r, 15000)
    }

    private fun cancelBufferWatch() {
        bufferWatch?.let { main.removeCallbacks(it) }
        bufferWatch = null
    }

    private fun autoSkip() {
        if (!ui.settings.skip || skipGuard) return
        skipGuard = true
        main.postDelayed({
            skipGuard = false
            next()
        }, 1000)
    }

    private fun playNow(st: Station) {
        val c = controller
        if (c == null) {
            pending = st
            connect()
            return
        }
        cancelBufferWatch()
        val builder = MediaItem.Builder()
            .setUri(st.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(st.name)
                    .setArtist("AIRWAVE · 中国网络收音机")
                    .build()
            )
        if (st.hls || st.url.contains(".m3u8", true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        c.setMediaItem(builder.build())
        c.prepare()
        c.play()
        playedId = st.id
        ui = ui.copy(loading = true, paused = false, lcdStatus = "正在连接…")
        applyVolume()
        if (firstPlay) {
            firstPlay = false
            onFirstPlay?.invoke()
        }
    }

    private fun applyVolume() {
        val c = controller ?: return
        c.volume = if (ui.muted) 0f else ui.volume / 100f
    }

    fun tick() {
        val t = nowText()
        if (t != ui.clock) ui = ui.copy(clock = t)
    }

    private fun nowText(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    // ---------------- 目录 ----------------

    private fun find(id: String): Station? =
        ui.catalog.firstOrNull { it.id == id }
            ?: ui.links.firstOrNull { "link:${it.url}" == id }?.let { Catalog.linkStation(it) }

    private fun poolFor(s: UiState): List<Station> {
        val base = when (s.nav) {
            1 -> s.catalog.filter { s.favs.contains(it.id) }
            2 -> s.recents.mapNotNull { find(it) }
            3 -> s.links.map { Catalog.linkStation(it) }
            else -> s.catalog
        }
        val q = s.query.trim().lowercase()
        return base.filter { st ->
            if (q.isNotEmpty()) {
                val hay = "${st.name} ${st.state} ${st.tags.joinToString(" ")} ${Catalog.provinceOf(st)}".lowercase()
                if (!hay.contains(q)) return@filter false
            }
            if (s.nav == 0) {
                if (s.tag.isNotEmpty() && st.tags.none { it.equals(s.tag, ignoreCase = true) }) return@filter false
                if (s.region.isNotEmpty() && !Catalog.matchesRegion(st, s.region)) return@filter false
            }
            true
        }
    }

    private fun rebuild(s: UiState = ui): UiState = s.copy(pool = poolFor(s))

    private fun persist() {
        store.save(
            Saved(
                favs = ui.favs.toList(),
                recents = ui.recents,
                links = ui.links,
                volume = ui.volume,
                settings = ui.settings,
                lastId = ui.current?.id,
            )
        )
    }

    fun setQuery(q: String) {
        ui = rebuild(ui.copy(query = q))
    }

    fun pickNav(i: Int) {
        ui = rebuild(ui.copy(nav = i, tag = "", region = "", query = ""))
    }

    fun pickTag(i: Int) {
        val key = Catalog.TAGS.getOrNull(i)?.second ?: return
        ui = rebuild(ui.copy(tag = if (ui.tag == key) "" else key))
    }

    fun pickRegion(label: String) {
        ui = rebuild(ui.copy(region = if (ui.region == label) "" else label))
    }

    fun clearFilters() {
        ui = rebuild(ui.copy(tag = "", region = ""))
    }

    fun openSettings() { ui = ui.copy(settingsOpen = true) }
    fun toggleSearch() {
        ui = if (ui.searchOpen) rebuild(ui.copy(searchOpen = false, query = ""))
        else ui.copy(searchOpen = true)
    }
    fun closeSearch() {
        if (!ui.searchOpen) return
        ui = rebuild(ui.copy(searchOpen = false, query = ""))
    }
    fun closeSettings() { ui = ui.copy(settingsOpen = false) }
    fun openFilter() { ui = ui.copy(filterOpen = true) }
    fun closeFilter() { ui = ui.copy(filterOpen = false) }
    fun openLink() { ui = ui.copy(linkOpen = true) }
    fun closeLink() { ui = ui.copy(linkOpen = false) }

    fun anyOverlay(): Boolean = ui.settingsOpen || ui.filterOpen || ui.linkOpen || ui.searchOpen

    fun closeOverlays() {
        ui = rebuild(ui.copy(settingsOpen = false, filterOpen = false, linkOpen = false, searchOpen = false, query = ""))
    }

    fun setOption(key: String, value: Boolean) {
        val s = when (key) {
            "autoplay" -> ui.settings.copy(autoplay = value)
            "resume" -> ui.settings.copy(resume = value)
            "background" -> ui.settings.copy(background = value)
            "shuffle" -> ui.settings.copy(shuffle = value)
            "skip" -> ui.settings.copy(skip = value)
            else -> ui.settings
        }
        ui = ui.copy(settings = s)
        persist()
    }

    // ---------------- 播放 ----------------

    fun playRow(pos: Int) {
        val st = ui.pool.getOrNull(pos) ?: return
        select(st, true)
    }

    private fun select(st: Station, autoplay: Boolean) {
        ui = ui.copy(
            current = st,
            lcdMode = Catalog.freqOf(st).second,
            muted = ui.muted,
        )
        if (ui.nav != 2) {
            val rec = (listOf(st.id) + ui.recents.filter { it != st.id }).take(40)
            ui = ui.copy(recents = rec, pool = poolFor(ui.copy(recents = rec)))
        }
        persist()
        if (autoplay) playNow(st)
        else ui = ui.copy(lcdStatus = "待机 STANDBY")
    }

    fun togglePlay() {
        val c = controller
        val st = ui.current
        if (c != null && c.isPlaying) {
            c.pause()
            ui = ui.copy(playing = false, paused = true, lcdStatus = "已暂停 PAUSED")
            return
        }
        if (st == null) {
            playRow(0)
            return
        }
        if (c != null && playedId == st.id && c.mediaItemCount > 0) {
            if (c.isCurrentMediaItemLive) c.seekToDefaultPosition()
            c.play()
        } else {
            playNow(st)
        }
    }

    fun next() = step(1)
    fun prev() = step(-1)

    private fun step(dir: Int) {
        val pool = ui.pool
        if (pool.size <= 1) return
        val idx = pool.indexOfFirst { it.id == ui.currentId }
        val pos = when {
            ui.settings.shuffle -> {
                if (pool.size <= 1) 0 else {
                    var p = (Math.random() * pool.size).toInt()
                    if (p == idx) p = (p + 1) % pool.size
                    p
                }
            }
            dir > 0 -> if (idx < 0 || idx + 1 >= pool.size) 0 else idx + 1
            else -> if (idx <= 0) pool.size - 1 else idx - 1
        }
        select(pool[pos], true)
    }

    fun adjustVolume(delta: Int) {
        val v = (ui.volume + delta).coerceIn(0, 100)
        ui = ui.copy(volume = v, muted = false)
        applyVolume()
        persist()
    }

    fun toggleMute() {
        ui = ui.copy(muted = !ui.muted)
        applyVolume()
    }

    fun toggleFav(id: String) {
        val favs = ui.favs.toMutableSet()
        if (!favs.remove(id)) favs.add(id)
        ui = rebuild(ui.copy(favs = favs))
        persist()
    }

    fun addLink(name: String, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            ui = ui.copy(lcdStatus = "地址需 http(s)")
            return
        }
        val rec = LinkRec(name.trim(), u)
        val links = (listOf(rec) + ui.links.filter { it.url != u }).take(60)
        ui = rebuild(ui.copy(links = links, nav = 3, tag = "", region = "", query = "", linkOpen = false))
        val st = Catalog.linkStation(rec)
        select(st, true)
    }

    fun pauseForBackground() {
        if (!ui.settings.background) {
            controller?.pause()
        }
    }
}

fun volumeToDegrees(vol: Int): Float = -132f + vol.coerceIn(0, 100) * 2.64f

fun pct(n: Int, total: Int): Int = if (total <= 0) 0 else (n * 100f / total).roundToInt()
