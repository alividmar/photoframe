package si.photoframe

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Station(val id: String, val name: String, val url: String)

fun newStationId(): String = UUID.randomUUID().toString().take(8)

/**
 * Privzete postaje. Vpisana je samo postaja, katere naslov sem nasel v javnem viru
 * (Wikipedia, polje "Webcast" za Val 202; direktorij postaj kaze isti streznik mp3.rtvslo.si).
 * Ostale ikone so prazne: dodaj jih v Nastavitvah ali uvozi seznam (M3U/PLS).
 */
object DefaultStations {
    val list = listOf(Station("val202", "Val 202", "http://mp3.rtvslo.si/val202"))
    val slots = listOf("val202", "", "", "")
}

object SlotCodec {
    fun encode(slots: List<String>): String = slots.joinToString(",")
    fun decode(raw: String): List<String> = (raw.split(",") + List(4) { "" }).take(4)
}

object StationJson {
    fun encode(list: List<Station>): String {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("url", s.url))
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
        for (l in lines) {
            when {
                l.startsWith("#EXTINF", ignoreCase = true) ->
                    title = l.substringAfter(',', "").trim().ifEmpty { null }
                l.startsWith("#") -> Unit
                isUrl(l) -> {
                    out.add(Station(newStationId(), title ?: nameFromUrl(l), l))
                    title = null
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
            append("#EXTINF:-1,").append(s.name.replace('\n', ' ')).append('\n')
            append(s.url).append('\n')
        }
    }
}
