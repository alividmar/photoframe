package si.photoframe

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
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
    var diagText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
    SliderRow("Privzeti izklop radia", s.defaultTimerMin.toFloat(), 0f..240f, { if (it < 1f) "brez" else "${it.toInt()} min" }) { v ->
        onChange { it.copy(defaultTimerMin = v.toInt()) }
    }
    Text(
        "Ko radio zacne igrati, se izklop nastavi sam (postaja z lastnim izklopom ima prednost). Z ikono casovnika ga lahko popravis.",
        color = Color.LightGray,
        fontSize = 12.sp,
    )
    SwitchRow("Ko radio igra: skrij uro", s.radioHideClock) { v -> onChange { it.copy(radioHideClock = v) } }
    SwitchRow("Ko radio igra: ugasni zaslon (sistem ga ugasne po casu mirovanja)", s.radioScreenOff) { v ->
        onChange { it.copy(radioScreenOff = v) }
    }
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
            onDiag = {
                diagText = "Preverjam ..."
                scope.launch { diagText = StreamTest.run(context, st) }
            },
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

    diagText?.let { text ->
        AlertDialog(
            onDismissRequest = { diagText = null },
            title = { Text("Diagnostika postaje") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(text, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("diagnostika", text))
                    Toast.makeText(context, "Kopirano.", Toast.LENGTH_SHORT).show()
                }) { Text("Kopiraj") }
            },
            dismissButton = { TextButton(onClick = { diagText = null }) { Text("Zapri") } },
        )
    }

    if (adding) {
        StationDialog(null, { adding = false }) { st ->
            adding = false
            onChange { cur -> cur.copy(stations = cur.stations + st) }
        }
    }
    editing?.let { old ->
        StationDialog(old, { editing = null }) { st ->
            editing = null
            onChange { cur -> cur.copy(stations = cur.stations.map { x -> if (x.id == old.id) st else x }) }
        }
    }
}

@Composable
private fun StationRow(
    st: Station,
    playing: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDiag: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(st.name, color = Color.White)
        Text(st.url, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
        if (st.autoOffMin > 0) {
            Text("Samodejni izklop po ${st.autoOffMin} min", color = Color(0xFF69F0AE), fontSize = 11.sp)
        }
        Row {
            TextButton(onClick = onToggle) { Text(if (playing) "Ustavi" else "Poslusaj") }
            TextButton(onClick = onEdit) { Text("Uredi") }
            TextButton(onClick = onDiag) { Text("Diagnostika") }
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
private fun StationDialog(initial: Station?, onDismiss: () -> Unit, onSave: (Station) -> Unit) {
    val context = LocalContext.current
    val id = remember { initial?.id ?: newStationId() }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var icon by remember { mutableStateOf(initial?.iconUrl ?: "") }
    var offText by remember { mutableStateOf((initial?.autoOffMin ?: 0).toString()) }
    val u = url.trim()
    val valid = u.startsWith("http://", true) || u.startsWith("https://", true)

    // Slika iz naprave se skopira v pomnilnik aplikacije (tako ni potrebna dolga povezava)
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val file = File(context.filesDir, "icon_${id}_${System.currentTimeMillis()}.img")
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
            }.isSuccess
            if (ok) {
                icon = "file://" + file.absolutePath
            } else {
                Toast.makeText(context, "Slike ni bilo mogoce prebrati.", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova postaja" else "Uredi postajo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Ime") }, singleLine = true,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Povezava (http:// ali https://)") }, singleLine = true,
                )
                OutlinedTextField(
                    value = icon, onValueChange = { icon = it },
                    label = { Text("Ikona: povezava (neobvezno)") }, singleLine = true,
                )
                OutlinedTextField(
                    value = offText,
                    onValueChange = { offText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Samodejni izklop po (minut, 0 = brez)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon.isNotBlank()) {
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    OutlinedButton(onClick = { pickImage.launch(arrayOf("image/*")) }) { Text("Izberi sliko ...") }
                    if (icon.isNotBlank()) TextButton(onClick = { icon = "" }) { Text("Odstrani") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val off = (offText.toIntOrNull() ?: 0).coerceIn(0, 720)
                    onSave(Station(id, name.trim().ifBlank { u }, u, icon.trim(), off))
                },
                enabled = valid,
            ) { Text("Shrani") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Preklici") } },
    )
}

/** Nastavitve spanja: kdaj se po zagonu radia zaslon zatemni in kaj se takrat prikaze. */
@Composable
internal fun NightSettingsSection(s: AppSettings, onChange: Change) {
    Title("Spanje (vecerna zatemnitev)")
    Text(
        "Ko po izbrani uri zacnes predvajati radio, se zaslon zatemni. Konec: ob jutranji uri ali ob zvonjenju alarma (ali takoj, ko radio ustavis, ce izklopis spodnjo moznost).",
        color = Color.LightGray,
        fontSize = 12.sp,
    )
    SwitchRow("Vklopljeno", s.nightEnabled) { v -> onChange { it.copy(nightEnabled = v) } }
    if (s.nightEnabled) {
        TimeRow("Zatemni, ce radio zacnes po", s.nightStartMin) { m -> onChange { it.copy(nightStartMin = m) } }
        TimeRow("Jutranja ura (konec)", s.nightEndMin) { m -> onChange { it.copy(nightEndMin = m) } }
        SwitchRow("Ostane zatemnjeno do jutra, tudi ko se radio ustavi", s.nightStayUntilMorning) { v ->
            onChange { it.copy(nightStayUntilMorning = v) }
        }
        SwitchRow("Ugasni zaslon (sistem ga ugasne po casu mirovanja)", s.nightScreenOff) { v ->
            onChange { it.copy(nightScreenOff = v) }
        }
        SwitchRow("Skrij uro ponoci", s.nightHideClock) { v -> onChange { it.copy(nightHideClock = v) } }
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
        SwitchRow("Ob zatemnitvi nastavi glasnost", s.nightVolumeEnabled) { v ->
            onChange { it.copy(nightVolumeEnabled = v) }
        }
        if (s.nightVolumeEnabled) {
            SliderRow("Glasnost", s.nightVolumePct.toFloat(), 0f..100f, { "${it.toInt()} %" }) { v ->
                onChange { it.copy(nightVolumePct = v.toInt()) }
            }
        }
    }
}

@Composable
internal fun TimeRow(label: String, minutes: Int, onPick: (Int) -> Unit) {
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
