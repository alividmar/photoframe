package si.photoframe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Nastavitve vremena: mesto (Open-Meteo), neobvezna postaja Weather Underground, trajanje napovedi. */
@Composable
internal fun WeatherSettingsSection(s: AppSettings, onChange: Change) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by WeatherState.data.collectAsState()
    val err by WeatherState.error.collectAsState()
    var query by remember { mutableStateOf(s.cityName) }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var info by remember { mutableStateOf<String?>(null) }
    var station by remember { mutableStateOf(s.wuStation) }
    var key by remember { mutableStateOf(s.wuApiKey) }

    Title("Vreme")
    SwitchRow("Prikazi vreme pod uro", s.weatherEnabled) { v -> onChange { it.copy(weatherEnabled = v) } }
    Text(
        "Temperatura je lahko z tvoje postaje Weather Underground, ikona stanja in tedenska napoved pa sta iz brezplacnega " +
            "Open-Meteo (brez kljuca, za nekomercialno uporabo). Dotik na vreme pokaze napoved za 7 dni.",
        color = Color.LightGray, fontSize = 12.sp,
    )

    Label("Mesto za ikono in napoved")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Mesto (npr. Postojna)") }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Button(enabled = query.isNotBlank(), onClick = {
            info = "Iscem ..."
            scope.launch {
                try {
                    results = WeatherClient.searchCity(context, query)
                    info = if (results.isEmpty()) "Ni zadetkov." else null
                } catch (e: Exception) {
                    info = "NAPAKA: " + NasClient.describe(e)
                }
            }
        }) { Text("Poisci") }
    }
    info?.let { Text(it, color = Color.LightGray, fontSize = 12.sp) }
    for (c in results) {
        TextButton(onClick = {
            onChange { it.copy(cityName = c.name, cityLat = c.lat, cityLon = c.lon) }
            query = c.name
            results = emptyList()
        }) { Text(listOf(c.name, c.region, c.country).filter { it.isNotBlank() }.joinToString(", ")) }
    }
    Text(
        if (s.cityName.isNotBlank()) "Izbrano mesto: ${s.cityName}" else "Mesto ni izbrano (brez njega ni ikone stanja in napovedi).",
        color = Color.White, fontSize = 13.sp,
    )

    Label("Weather Underground (neobvezno, samo za temperaturo)")
    OutlinedTextField(
        value = station, onValueChange = { station = it; onChange { c -> c.copy(wuStation = it.trim()) } },
        label = { Text("ID postaje (npr. IPOSTO50)") }, singleLine = true,
    )
    OutlinedTextField(
        value = key, onValueChange = { key = it; onChange { c -> c.copy(wuApiKey = it.trim()) } },
        label = { Text("API kljuc") }, singleLine = true,
    )
    Text(
        "API kljuc dobijo lastniki postaj v racunu na wunderground.com (Member Settings, API Keys); kljuc je treba cez cas obnoviti. " +
            "Brez kljuca se uporabi temperatura iz Open-Meteo.",
        color = Color.LightGray, fontSize = 12.sp,
    )

    SliderRow("Napoved se prikaze za", s.forecastSec.toFloat(), 3f..60f, { "${it.toInt()} s" }) { v ->
        onChange { it.copy(forecastSec = v.toInt()) }
    }

    OutlinedButton(modifier = Modifier.padding(top = 8.dp), onClick = {
        scope.launch {
            try {
                WeatherState.data.value = WeatherClient.fetch(context, s)
                WeatherState.error.value = null
            } catch (e: Exception) {
                WeatherState.error.value = NasClient.describe(e)
            }
        }
    }) { Text("Osvezi zdaj") }

    val d = data
    if (d != null) {
        val t = d.tempC?.let { "%.1f °C".format(it) } ?: "--"
        Text("Zadnji podatki: $t (vir temperature: ${d.tempSource.ifBlank { "-" }}), napoved: ${d.days.size} dni", color = Color(0xFF69F0AE), fontSize = 12.sp)
        d.wuError?.let { Text("Weather Underground: $it", color = Color(0xFFFF8A65), fontSize = 12.sp) }
    }
    err?.let { Text("Napaka: $it", color = Color(0xFFFF8A65), fontSize = 12.sp) }
}
