package si.photoframe

import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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

internal typealias Change = ((AppSettings) -> AppSettings) -> Unit

/** Nastavitve radia: postaje, ikone v spodnji vrstici, uvoz in izvoz. */
@Composable
internal fun RadioSettingsSection(s: AppSettings, onChange: Change) {
    val context = LocalContext.current
    val status by RadioState.status.collectAsState()
    val playing by RadioState.station.collectAsState()
    var editing by remember { mutableStateOf<Station?>(null) }
    var adding by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > 1_000_000) null else String(bytes, Charsets.UTF_8)
                }
            }.getOrNull()
            val parsed = if (text == null) emptyList() else PlaylistIO.parse(text)
            val known = s.stations.map { it.url }.toSet()
            val fresh = parsed.filter { it.url !in known }.distinctBy { it.url }
            if (fresh.isNotEmpty()) onChange { cur -> cur.copy(stations = cur.stations + fresh) }
            val msg = if (fresh.isEmpty()) "Ni novih postaj za uvoz." else "Uvozenih postaj: ${fresh.size}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(PlaylistIO.toM3u(s.stations).toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "Seznam izvozen." else "Izvoz ni uspel.", Toast.LENGTH_LONG).show()
        }
    }

    Title("Radio")
    Label("Ikone v spodnji vrstici")
    for (i in 0 until 4) SlotPicker(i, s, onChange)

    Label("Postaje")
    if (s.stations.isEmpty()) {
        Text("Seznam je prazen. Dodaj postajo ali uvozi seznam.", color = Color.LightGray, fontSize = 12.sp)
    }
    for (st in s.stations) {
        val isPlaying = playing?.id == st.id && (status == RadioStatus.LOADING || status == RadioStatus.PLAYING)
        StationRow(
            st = st,
            playing = isPlaying,
            onToggle = { if (isPlaying) RadioService.stop(context) else RadioService.play(context, st) },
            onEdit = { editing = st },
            onDelete = {
                onChange { cur ->
                    cur.copy(
                        stations = cur.stations.filter { it.id != st.id },
                        slotIds = cur.slotIds.map { id -> if (id == st.id) "" else id },
                    )
                }
            },
        )
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { adding = true }) { Text("Dodaj postajo") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Uvozi (M3U/PLS)") }
        OutlinedButton(onClick = { exportLauncher.launch("postaje.m3u") }) { Text("Izvozi (M3U)") }
    }

    if (adding) {
        StationDialog(null, { adding = false }) { name, url ->
            adding = false
            val u = url.trim()
            onChange { cur -> cur.copy(stations = cur.stations + Station(newStationId(), name.trim().ifBlank { u }, u)) }
        }
    }
    editing?.let { st ->
        StationDialog(st, { editing = null }) { name, url ->
            editing = null
            val u = url.trim()
            onChange { cur ->
                cur.copy(
                    stations = cur.stations.map { x ->
                        if (x.id == st.id) x.copy(name = name.trim().ifBlank { u }, url = u) else x
                    },
                )
            }
        }
    }
}

@Composable
private fun StationRow(st: Station, playing: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(st.name, color = Color.White)
        Text(st.url, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
        Row {
            TextButton(onClick = onToggle) { Text(if (playing) "Ustavi" else "Poslusaj") }
            TextButton(onClick = onEdit) { Text("Uredi") }
            TextButton(onClick = onDelete) { Text("Izbrisi") }
        }
    }
}

@Composable
private fun SlotPicker(index: Int, s: AppSettings, onChange: Change) {
    var open by remember { mutableStateOf(false) }
    val currentId = s.slotIds.getOrNull(index) ?: ""
    val current = s.stations.firstOrNull { it.id == currentId }

    fun setSlot(id: String) {
        onChange { cur -> cur.copy(slotIds = List(4) { i -> if (i == index) id else cur.slotIds.getOrNull(i) ?: "" }) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Ikona ${index + 1}:", color = Color.White, modifier = Modifier.padding(end = 12.dp))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(current?.name ?: "(prazno)") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("(prazno)") }, onClick = { open = false; setSlot("") })
                for (st in s.stations) {
                    DropdownMenuItem(text = { Text(st.name) }, onClick = { open = false; setSlot(st.id) })
                }
            }
        }
    }
}

@Composable
private fun StationDialog(initial: Station?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    val u = url.trim()
    val valid = u.startsWith("http://", true) || u.startsWith("https://", true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova postaja" else "Uredi postajo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Ime") }, singleLine = true,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Povezava (http:// ali https://)") }, singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, url) }, enabled = valid) { Text("Shrani") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Preklici") } },
    )
}

/** Nastavitve spanja: kdaj se po zagonu radia zaslon zatemni in kaj se takrat prikaze. */
@Composable
internal fun NightSettingsSection(s: AppSettings, onChange: Change) {
    Title("Spanje (vecerna zatemnitev)")
    Text(
        "Ko po izbrani uri zacnes predvajati radio, se zaslon zatemni. Konec: ko radio ustavis ali ko mine jutranja ura.",
        color = Color.LightGray,
        fontSize = 12.sp,
    )
    SwitchRow("Vklopljeno", s.nightEnabled) { v -> onChange { it.copy(nightEnabled = v) } }
    if (s.nightEnabled) {
        TimeRow("Zatemni, ce radio zacnes po", s.nightStartMin) { m -> onChange { it.copy(nightStartMin = m) } }
        TimeRow("Jutranja ura (konec)", s.nightEndMin) { m -> onChange { it.copy(nightEndMin = m) } }
        SwitchRow("Izklopi diaprojekcijo (enobarvno ozadje)", s.nightPhotosOff) { v ->
            onChange { it.copy(nightPhotosOff = v) }
        }
        if (s.nightPhotosOff) {
            Label("Barva ozadja")
            ColorRow(s.nightBgColor) { v -> onChange { it.copy(nightBgColor = v) } }
        }
        SliderRow("Svetlost zaslona", s.nightBrightness, 0.01f..1f, { "${(it * 100).toInt()} %" }) { v ->
            onChange { it.copy(nightBrightness = v) }
        }
        SliderRow("Svetlost ure in ikon", s.nightClockAlpha, 0.1f..1f, { "${(it * 100).toInt()} %" }) { v ->
            onChange { it.copy(nightClockAlpha = v) }
        }
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            TimePickerDialog(
                context,
                { _, h, m -> onPick(h * 60 + m) },
                minutes / 60, minutes % 60, true,
            ).show()
        }) { Text("%02d:%02d".format(minutes / 60, minutes % 60)) }
    }
}
