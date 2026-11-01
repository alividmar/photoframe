package si.photoframe

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DayNames = listOf("Po", "To", "Sr", "Ce", "Pe", "So", "Ne")

/** Celozaslonski prikaz med zvonjenjem alarma. */
@Composable
fun AlarmOverlay(index: Int, cfg: AlarmConfig?, onStop: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xF2000000)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Alarm ${index + 1}", color = Color.White, fontSize = 32.sp)
            if (cfg != null) {
                Text("%02d:%02d".format(cfg.hour, cfg.minute), color = Color.White, fontSize = 80.sp)
            }
            Button(onClick = onStop, modifier = Modifier.padding(top = 40.dp).size(width = 280.dp, height = 100.dp)) {
                Text("USTAVI", fontSize = 30.sp)
            }
        }
    }
}

/** Nastavitve obeh alarmov: ura, dnevi, ton ali radio, glasnost zbujanja. */
@Composable
internal fun AlarmSettingsSection(s: AppSettings, onChange: Change) {
    Title("Alarmi")
    Text(
        "Ikoni alarma v spodnji vrstici vklopita ali izklopita posamezni alarm. Ce ni izbran noben dan, alarm zazvoni samo enkrat.",
        color = Color.LightGray,
        fontSize = 12.sp,
    )
    for (i in s.alarms.indices) AlarmEditor(i, s, onChange)
}

@Composable
private fun AlarmEditor(i: Int, s: AppSettings, onChange: Change) {
    val context = LocalContext.current
    val a = s.alarms[i]
    val station = s.stations.firstOrNull { it.id == a.stationId }

    fun update(f: (AlarmConfig) -> AlarmConfig) {
        onChange { cur -> cur.copy(alarms = cur.alarms.mapIndexed { idx, x -> if (idx == i) f(x) else x }) }
    }

    Text("Alarm ${i + 1}", color = Color(0xFFFFEB3B), fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
    SwitchRow("Vklopljen", a.enabled) { v -> update { it.copy(enabled = v) } }
    TimeRow("Ura", a.hour * 60 + a.minute) { m -> update { it.copy(hour = m / 60, minute = m % 60) } }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayNames.forEachIndexed { d, name ->
            FilterChip(
                selected = ((a.days shr d) and 1) == 1,
                onClick = { update { it.copy(days = it.days xor (1 shl d)) } },
                label = { Text(name) },
            )
        }
    }

    ChipRow(listOf(false to "Ton", true to "Radio"), a.useRadio) { v -> update { it.copy(useRadio = v) } }
    if (a.useRadio) {
        var open by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { open = true }) { Text(station?.name ?: "(izberi postajo)") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (st in s.stations) {
                    DropdownMenuItem(text = { Text(st.name) }, onClick = { open = false; update { it.copy(stationId = st.id) } })
                }
            }
        }
        if (station == null) {
            Text("Brez postaje bo zazvonil ton.", color = Color(0xFFFF8A65), fontSize = 12.sp)
        }
    } else {
        Text("Ton: sistemski ton alarma te naprave.", color = Color.LightGray, fontSize = 12.sp)
    }
    SliderRow("Glasnost zbujanja", a.volumePct.toFloat(), 0f..100f, { "${it.toInt()} %" }) { v ->
        update { it.copy(volumePct = v.toInt()) }
    }
    OutlinedButton(onClick = { AlarmService.start(context, i, a, station) }) { Text("Preizkusi zdaj") }
}
