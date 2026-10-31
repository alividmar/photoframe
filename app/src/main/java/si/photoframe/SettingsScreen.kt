package si.photoframe

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Palette = listOf(
    0xFFFFFFFFL, 0xFFFFEB3BL, 0xFFFF9800L, 0xFFF44336L,
    0xFF4CAF50L, 0xFF00BCD4L, 0xFF2196F3L, 0xFF000000L,
    0xFF212121L, 0xFF0D1B2AL,
)

/** Celozaslonske nastavitve. Vsaka sprememba se takoj shrani. */
@Composable
fun SettingsScreen(
    s: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            // Dovoljenje za branje mape obdrzimo tudi po ponovnem zagonu aplikacije
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onChange { it.copy(folderUri = uri.toString()) }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nastavitve", color = Color.White, fontSize = 28.sp, modifier = Modifier.weight(1f))
                Button(onClick = onClose) { Text("Zapri") }
            }

            // Predogled ure
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF455A64))
                    .padding(16.dp),
                contentAlignment = s.clockPosition.alignment(),
            ) { ClockOverlay(s) }

            Title("Slike")
            Button(onClick = { picker.launch(null) }) { Text("Izberi mapo s slikami") }
            Text(
                text = s.folderUri?.let { Uri.decode(it) } ?: "Mapa ni izbrana",
                color = Color.LightGray,
                fontSize = 12.sp,
            )
            SliderRow("Cas med slikami", s.intervalSec.toFloat(), 3f..120f, { "${it.toInt()} s" }) { v ->
                onChange { it.copy(intervalSec = v.toInt()) }
            }
            SwitchRow("Nakljucni vrstni red", s.shuffle) { v -> onChange { it.copy(shuffle = v) } }
            Label("Prikaz slike")
            ChipRow(
                listOf(ImageScale.FIT to "Cela slika, ostalo crno", ImageScale.FILL to "Napolni zaslon (obreze)"),
                s.imageScale,
            ) { v -> onChange { it.copy(imageScale = v) } }
            SliderRow("Trajanje prehoda", s.fadeMs / 1000f, 0f..6f, { if (it < 0.1f) "brez" else "%.1f s".format(it) }) { v ->
                onChange { it.copy(fadeMs = (v * 1000).toInt()) }
            }
            SwitchRow("Prehod prek crnine (milejse pri veliki razliki svetlosti)", s.fadeThroughBlack) { v ->
                onChange { it.copy(fadeThroughBlack = v) }
            }

            Title("Ura")
            ChipRow(
                listOf(ClockStyle.DIGITAL to "Digitalna", ClockStyle.ANALOG to "Analogna", ClockStyle.OFF to "Izklopljena"),
                s.clockStyle,
            ) { v -> onChange { it.copy(clockStyle = v) } }

            Label("Polozaj")
            ChipRow(
                listOf(
                    ClockPosition.TOP_LEFT to "Zgoraj levo",
                    ClockPosition.TOP_RIGHT to "Zgoraj desno",
                    ClockPosition.CENTER to "Sredina",
                    ClockPosition.BOTTOM_LEFT to "Spodaj levo",
                    ClockPosition.BOTTOM_RIGHT to "Spodaj desno",
                ),
                s.clockPosition,
            ) { v -> onChange { it.copy(clockPosition = v) } }

            Label("Barva ure")
            ColorRow(s.clockColor) { v -> onChange { it.copy(clockColor = v) } }
            Label("Barva ozadja ure")
            ColorRow(s.clockBgColor) { v -> onChange { it.copy(clockBgColor = v) } }
            SliderRow("Prosojnost ozadja", s.clockBgAlpha, 0f..1f, { "${(it * 100).toInt()} %" }) { v ->
                onChange { it.copy(clockBgAlpha = v) }
            }
            SliderRow("Velikost ure", s.clockScale, 0.5f..2.5f, { "${(it * 100).toInt()} %" }) { v ->
                onChange { it.copy(clockScale = v) }
            }

            if (s.clockStyle == ClockStyle.DIGITAL) {
                Label("Pisava")
                ChipRow(
                    listOf(DigitalFont.SANS to "Moderna", DigitalFont.SERIF to "Klasicna", DigitalFont.MONO to "Strojna"),
                    s.digitalFont,
                ) { v -> onChange { it.copy(digitalFont = v) } }
                SwitchRow("24-urni prikaz", s.use24h) { v -> onChange { it.copy(use24h = v) } }
                SwitchRow("Prikazi datum", s.showDate) { v -> onChange { it.copy(showDate = v) } }
            }
            if (s.clockStyle == ClockStyle.ANALOG) {
                Label("Obroc")
                ChipRow(
                    listOf(AnalogFace.TICKS to "Crtice", AnalogFace.DOTS to "Pike", AnalogFace.NONE to "Brez"),
                    s.analogFace,
                ) { v -> onChange { it.copy(analogFace = v) } }
            }
            if (s.clockStyle != ClockStyle.OFF) {
                SwitchRow("Prikazi sekunde", s.showSeconds) { v -> onChange { it.copy(showSeconds = v) } }
            }

            Title("OLED zascita")
            Text(
                "Ura se premika v okolici polozaja, ki si ga izbral zgoraj.",
                color = Color.LightGray,
                fontSize = 12.sp,
            )
            SwitchRow("Premikaj uro po zaslonu", s.oledMoveClock) { v -> onChange { it.copy(oledMoveClock = v) } }
            SwitchRow("Premikaj ikone spodaj", s.oledMoveBar) { v -> onChange { it.copy(oledMoveBar = v) } }
            if (s.oledMoveClock) {
                SliderRow("Obseg premika ure", s.oledRange, 0.1f..1f, { "${(it * 100).toInt()} %" }) { v ->
                    onChange { it.copy(oledRange = v) }
                }
            }
            if (s.oledMoveClock || s.oledMoveBar) {
                SliderRow("Premik na vsakih", s.oledMoveMinutes.toFloat(), 1f..30f, { "${it.toInt()} min" }) { v ->
                    onChange { it.copy(oledMoveMinutes = v.toInt()) }
                }
            }
            SwitchRow("Skrij ikone, pokazi jih na dotik", s.barAutoHide) { v -> onChange { it.copy(barAutoHide = v) } }
            if (s.barAutoHide) {
                SliderRow("Skrij po", s.barHideSec.toFloat(), 3f..60f, { "${it.toInt()} s" }) { v ->
                    onChange { it.copy(barHideSec = v.toInt()) }
                }
            }

            RadioSettingsSection(s, onChange)
            NightSettingsSection(s, onChange)
        }
    }
}

@Composable
internal fun Title(text: String) {
    Text(text, color = Color(0xFF90CAF9), fontSize = 20.sp, modifier = Modifier.padding(top = 16.dp))
}

@Composable
internal fun Label(text: String) {
    Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onFinished: (Float) -> Unit,
) {
    // Med vlecenjem drsnika hranimo vrednost lokalno, shranimo sele ob koncu
    var v by remember(value) { mutableFloatStateOf(value) }
    Text("$label: ${valueText(v)}", color = Color.White)
    Slider(
        value = v,
        onValueChange = { v = it },
        valueRange = range,
        onValueChangeFinished = { onFinished(v) },
    )
}

@Composable
internal fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, name) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
internal fun ColorRow(selected: Long, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Palette.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color.Gray, CircleShape)
                    .clickable { onSelect(argb) },
            )
        }
    }
}
