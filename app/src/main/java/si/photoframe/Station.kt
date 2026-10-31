package si.photoframe

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Station(val id: String, val name: String, val url: String, val iconUrl: String = "", val autoOffMin: Int = 0)

fun newStationId(): String = UUID.randomUUID().toString().take(8)

/** Privzete postaje (naslove in ikone je podal uporabnik). Veljajo samo pri prvem zagonu. */
object DefaultStations {
    val list = listOf(
        Station(
            "val202", "Val 202", "http://mp3.rtvslo.si/val202",
            "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT6bHTwsHkZ-JRo963AWzAzSGxjIf6zkuvbGsWf5D35MTUzvvUyRuUjrgA&s=10",
        ),
        Station(
            "rakp", "Radio Koper", "http://mp3.rtvslo.si:8000/rakp",
            "https://img.rtvcdn.si/_up/ava/ava_misc/show_logos/173250939/logo.jpg",
        ),
        Station(
            "capris", "Radio Capris", "https://stream.exit.si/live",
            "https://www.radiocapris.si/data/images/streams/live.png?t=3",
        ),
        Station(
            "rock", "Radio Rock", "https://radio.svet24.si/Radio1Rock-v-zivo",
            "https://encrypted-tbn1.gstatic.com/images?q=tbn:ANd9GcS-yqwLjXEC16erbNTF5E8XmeEg-cv2npPiKw8LBW7R_33jZV8l",
        ),
    )
    val slots = listOf("val202", "rakp", "capris", "rock")
}

object SlotCodec {
    fun encode(slots: List<String>): String = slots.joinToString(",")
    fun decode(raw: String): List<String> = (raw.split(",") + List(4) { "" }).take(4)
}

object StationJson {
    fun encode(list: List<Station>): String {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("url", s.url).put("icon", s.iconUrl).put("off", s.autoOffMin))
        }
        return arr.toString()
    }

    fun decode(raw: String): List<Station> = runCatching {
        val arr = JSONArray(raw)
        val out = ArrayList<Station>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                Station(
                    id = o.optString("id").ifBlank { newStationId() },
                    name = o.optString("name").ifBlank { url },
                    url = url,
                    iconUrl = o.optString("icon"),
                    autoOffMin = o.optInt("off", 0),
                ),
            )
        }
        out
    }.getOrDefault(emptyList())
}

/** Uvoz in izvoz seznamov postaj v obicajnih formatih M3U/M3U8 in PLS. */
object PlaylistIO {
    private fun isUrl(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true)
    private fun nameFromUrl(url: String): String = Uri.parse(url).host ?: url

    fun parse(text: String): List<Station> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.any { it.equals("[playlist]", ignoreCase = true) }) parsePls(lines) else parseM3u(lines)
    }

    private fun parseM3u(lines: List<String>): List<Station> {
        val out = ArrayList<Station>()
        var title: String? = null
        var icon = ""
        val attr = Regex("[\\w-]+=\"[^\"]*\"")
        val logo = Regex("tvg-logo=\"([^\"]*)\"")
        val timerRe = Regex("x-timer=\"(\\d+)\"")
        var timer = 0
        for (l in lines) {
            when {
                l.startsWith("#EXTINF", ignoreCase = true) -> {
                    icon = logo.find(l)?.groupValues?.get(1).orEmpty()
                    timer = timerRe.find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    // Atribute (tvg-logo="...") odstranimo, da vejica v povezavi ne zmoti imena
                    title = l.replace(attr, "").substringAfter(',', "").trim().ifEmpty { null }
                }
                l.startsWith("#") -> Unit
                isUrl(l) -> {
                    out.add(Station(newStationId(), title ?: nameFromUrl(l), l, icon, timer))
                    title = null
                    icon = ""
                    timer = 0
                }
            }
        }
        return out
    }

    private fun parsePls(lines: List<String>): List<Station> {
        val files = sortedMapOf<Int, String>()
        val titles = HashMap<Int, String>()
        for (l in lines) {
            val eq = l.indexOf('=')
            if (eq < 0) continue
            val key = l.substring(0, eq).trim().lowercase()
            val value = l.substring(eq + 1).trim()
            if (key.startsWith("file")) {
                key.removePrefix("file").toIntOrNull()?.let { files[it] = value }
            } else if (key.startsWith("title")) {
                key.removePrefix("title").toIntOrNull()?.let { titles[it] = value }
            }
        }
        val out = ArrayList<Station>()
        for ((n, url) in files) {
            if (!isUrl(url)) continue
            val title = titles[n]
            out.add(Station(newStationId(), if (title.isNullOrBlank()) nameFromUrl(url) else title, url))
        }
        return out
    }

    fun toM3u(list: List<Station>): String = buildString {
        append("#EXTM3U\n")
        for (s in list) {
            val logo = if (isUrl(s.iconUrl)) " tvg-logo=\"${s.iconUrl}\"" else ""
            val off = if (s.autoOffMin > 0) " x-timer=\"${s.autoOffMin}\"" else ""
            append("#EXTINF:-1").append(logo).append(off).append(',').append(s.name.replace('\n', ' ')).append('\n')
            append(s.url).append('\n')
        }
    }
}
