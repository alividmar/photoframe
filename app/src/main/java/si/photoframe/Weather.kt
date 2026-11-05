package si.photoframe

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class DayForecast(val date: String, val code: Int, val max: Double, val min: Double, val rainPct: Int)

/**
 * Podatki o vremenu. tempC: temperatura (iz postaje Weather Underground, ce je nastavljena, sicer iz Open-Meteo).
 * code: koda stanja WMO (iz Open-Meteo), -1 = ni podatka.
 */
data class WeatherData(
    val tempC: Double?,
    val code: Int,
    val isDay: Boolean,
    val tempSource: String,
    val days: List<DayForecast>,
    val updated: Long,
    val wuError: String? = null,
)

data class City(val name: String, val region: String, val country: String, val lat: Double, val lon: Double)

object WeatherState {
    val data = MutableStateFlow<WeatherData?>(null)
    val error = MutableStateFlow<String?>(null)
}

object WeatherClient {
    private fun fmt(d: Double) = String.format(Locale.US, "%.4f", d)

    suspend fun fetch(context: Context, s: AppSettings): WeatherData = withContext(Dispatchers.IO) {
        var temp: Double? = null
        var source = ""
        var wuError: String? = null
        if (s.wuStation.isNotBlank() && s.wuApiKey.isNotBlank()) {
            try {
                temp = wuTemperature(context, s.wuStation.trim(), s.wuApiKey.trim())
                source = s.wuStation.trim()
            } catch (e: Exception) {
                wuError = NasClient.describe(e)
            }
        }
        var code = -1
        var isDay = true
        var days: List<DayForecast> = emptyList()
        val hasCity = s.cityName.isNotBlank() && !(s.cityLat == 0.0 && s.cityLon == 0.0)
        if (hasCity) {
            val om = openMeteo(context, s.cityLat, s.cityLon)
            if (temp == null) {
                temp = om.tempC
                source = "Open-Meteo"
            }
            code = om.code
            isDay = om.isDay
            days = om.days
        }
        if (temp == null && code < 0) {
            throw IOException("Ni podatkov: izberi mesto ali vnesi postajo in API kljuc" + (wuError?.let { " (WU: $it)" } ?: ""))
        }
        WeatherData(temp, code, isDay, source, days, System.currentTimeMillis(), wuError)
    }

    private fun wuTemperature(context: Context, station: String, key: String): Double {
        val url = "https://api.weather.com/v2/pws/observations/current".toHttpUrl().newBuilder()
            .addQueryParameter("apiKey", key).addQueryParameter("stationId", station)
            .addQueryParameter("format", "json").addQueryParameter("units", "m")
            .addQueryParameter("numericPrecision", "decimal").build()
        SecureHttp.client(context).newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IOException("Neveljaven ali potekel API kljuc (HTTP ${r.code})")
            if (r.code == 204) throw IOException("Postaja trenutno ne poroca podatkov (HTTP 204)")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val obs = JSONObject(r.body?.string().orEmpty()).getJSONArray("observations").getJSONObject(0)
            val t = obs.getJSONObject("metric").optDouble("temp", Double.NaN)
            if (t.isNaN()) throw IOException("Postaja ne vrne temperature")
            return t
        }
    }

    private class OpenMeteo(val tempC: Double?, val code: Int, val isDay: Boolean, val days: List<DayForecast>)

    private fun openMeteo(context: Context, lat: Double, lon: Double): OpenMeteo {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", fmt(lat)).addQueryParameter("longitude", fmt(lon))
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", "auto").addQueryParameter("forecast_days", "7").build()
        SecureHttp.client(context).newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Open-Meteo: HTTP ${r.code}")
            val o = JSONObject(r.body?.string().orEmpty())
            val cur = o.getJSONObject("current")
            val t = cur.optDouble("temperature_2m", Double.NaN)
            val d = o.getJSONObject("daily")
            val times = d.getJSONArray("time")
            val codes = d.getJSONArray("weather_code")
            val maxs = d.getJSONArray("temperature_2m_max")
            val mins = d.getJSONArray("temperature_2m_min")
            val rain = d.optJSONArray("precipitation_probability_max")
            val days = ArrayList<DayForecast>()
            for (i in 0 until times.length()) {
                days.add(
                    DayForecast(
                        times.getString(i), codes.optInt(i, -1),
                        maxs.optDouble(i, Double.NaN), mins.optDouble(i, Double.NaN),
                        rain?.optInt(i, 0) ?: 0,
                    ),
                )
            }
            return OpenMeteo(if (t.isNaN()) null else t, cur.optInt("weather_code", -1), cur.optInt("is_day", 1) == 1, days)
        }
    }

    suspend fun searchCity(context: Context, query: String): List<City> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim()).addQueryParameter("count", "6")
            .addQueryParameter("language", "sl").addQueryParameter("format", "json").build()
        SecureHttp.client(context).newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Iskanje mesta: HTTP ${r.code}")
            val arr = JSONObject(r.body?.string().orEmpty()).optJSONArray("results") ?: return@use emptyList<City>()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                City(o.optString("name"), o.optString("admin1"), o.optString("country"), o.getDouble("latitude"), o.getDouble("longitude"))
            }
        }
    }
}

/** Ikona stanja iz kode WMO (vektorske ikone, da delujejo tudi na starih napravah). */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val sun = Color(0xFFFFD54F)
    val grey = Color(0xFFB0BEC5)
    if (code == 1 || code == 2) {
        Box(modifier.size(size)) {
            Icon(
                if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay, null, tint = sun,
                modifier = Modifier.size(size * 0.8f).align(Alignment.TopStart),
            )
            Icon(Icons.Filled.Cloud, null, tint = Color.White, modifier = Modifier.size(size * 0.65f).align(Alignment.BottomEnd))
        }
        return
    }
    val icon = when (code) {
        0 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
        45, 48 -> Icons.Filled.Dehaze
        in 51..67, in 80..82 -> Icons.Filled.Opacity
        in 71..77, 85, 86 -> Icons.Filled.AcUnit
        95, 96, 99 -> Icons.Filled.FlashOn
        else -> Icons.Filled.Cloud
    }
    val tint = when (code) {
        0 -> sun
        in 51..67, in 80..82 -> Color(0xFF64B5F6)
        in 71..77, 85, 86 -> Color.White
        95, 96, 99 -> Color(0xFFFFB74D)
        else -> grey
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

private fun tempText(t: Double?): String = if (t == null || t.isNaN()) "--" else "%.0f°".format(t)

/** Vrstica z ikono in temperaturo pod uro; dotik pokaze tedensko napoved. */
@Composable
fun WeatherRow(w: WeatherData, color: Color, scale: Float, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (w.code >= 0) WeatherIcon(w.code, w.isDay, (36 * scale).dp)
        Text(tempText(w.tempC), color = color, fontSize = (32 * scale).sp)
    }
}

/** Tedenska napoved; zapre se sama po nastavljenem casu ali ob dotiku. */
@Composable
fun ForecastPanel(w: WeatherData, city: String, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE6000000))
            .clickable(onClick = onClose)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (city.isNotBlank()) Text(city, color = Color.White, fontSize = 22.sp)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (d in w.days) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val day = runCatching {
                        LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }.getOrDefault(d.date)
                    Text(day, color = Color.White, fontSize = 16.sp)
                    if (d.code >= 0) WeatherIcon(d.code, true, 40.dp, Modifier.padding(vertical = 6.dp))
                    Text(tempText(d.max), color = Color.White, fontSize = 18.sp)
                    Text(tempText(d.min), color = Color(0xFF90CAF9), fontSize = 15.sp)
                    Text("${d.rainPct} %", color = Color(0xFF64B5F6), fontSize = 12.sp)
                }
            }
        }
    }
}
