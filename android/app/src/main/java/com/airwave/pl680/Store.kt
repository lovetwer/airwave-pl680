package com.airwave.pl680

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val autoplay: Boolean = true,
    val resume: Boolean = true,
    val background: Boolean = true,
    val shuffle: Boolean = false,
    val skip: Boolean = true,
)

data class Saved(
    val favs: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val links: List<LinkRec> = emptyList(),
    val volume: Int = 70,
    val settings: Settings = Settings(),
    val lastId: String? = null,
)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("airwave", Context.MODE_PRIVATE)

    fun load(): Saved {
        val raw = sp.getString("json", null) ?: return Saved()
        return try {
            val o = JSONObject(raw)
            val favs = o.optJSONArray("favs")?.let { arr -> List(arr.length()) { arr.optString(it) } } ?: emptyList()
            val recents = o.optJSONArray("recents")?.let { arr -> List(arr.length()) { arr.optString(it) } } ?: emptyList()
            val links = o.optJSONArray("links")?.let { arr ->
                List(arr.length()) {
                    val l = arr.getJSONObject(it)
                    LinkRec(l.optString("name"), l.optString("url"))
                }
            } ?: emptyList()
            val s = o.optJSONObject("settings")
            Saved(
                favs = favs,
                recents = recents,
                links = links,
                volume = o.optInt("volume", 70).coerceIn(0, 100),
                settings = Settings(
                    autoplay = s?.optBoolean("autoplay", true) ?: true,
                    resume = s?.optBoolean("resume", true) ?: true,
                    background = s?.optBoolean("background", true) ?: true,
                    shuffle = s?.optBoolean("shuffle", false) ?: false,
                    skip = s?.optBoolean("skip", true) ?: true,
                ),
                lastId = o.optString("lastId", "").ifBlank { null },
            )
        } catch (e: Exception) {
            Saved()
        }
    }

    fun save(s: Saved) {
        val o = JSONObject()
        o.put("favs", JSONArray(s.favs))
        o.put("recents", JSONArray(s.recents))
        o.put("links", JSONArray().apply {
            s.links.forEach { put(JSONObject().put("name", it.name).put("url", it.url)) }
        })
        o.put("volume", s.volume)
        o.put("settings", JSONObject().apply {
            put("autoplay", s.settings.autoplay)
            put("resume", s.settings.resume)
            put("background", s.settings.background)
            put("shuffle", s.settings.shuffle)
            put("skip", s.settings.skip)
        })
        o.put("lastId", s.lastId ?: "")
        sp.edit().putString("json", o.toString()).apply()
    }
}
