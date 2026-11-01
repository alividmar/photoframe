package si.photoframe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Spodnja vrstica: 4 radijske postaje, casovnik izklopa (le ko radio igra), 2 alarma, nastavitve.
 * scale poveca ikone (1 = osnovna velikost); ce vse ne gre na zaslon, se velikost samodejno zmanjsa.
 */
@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    shiftX: Float = 0.5f,   // 0..1: vodoravni premik ikon (OLED)
    shiftY: Float = 0f,     // 0..1: navpicni premik ikon (OLED)
    scale: Float = 2f,
    slots: List<Station?>,
    activeId: String?,
    loading: Boolean,
    radioOn: Boolean,
    timerEndsAt: Long,
    alarms: List<AlarmConfig>,
    onSlot: (Int) -> Unit,
    onTimer: (Int) -> Unit,
    onAlarm: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .navigationBarsPadding(),
    ) {
        // Ena ikona je siroka (40 * k + 12) dp; poleg tega sta v vrstici se stranska odmika (skupaj 80 dp).
        // Ce pri zeleni velikosti vse ne gre v eno vrstico, ikone razporedimo v dve (postaje / alarmi in nastavitve),
        // da ostanejo res povecane.
        val n1 = 4 + (if (radioOn) 1 else 0)
        val n2 = alarms.size + 1
        fun fitFor(n: Int): Float = ((maxWidth.value - 80f) / n - 12f) / 40f
        val twoRows = fitFor(n1 + n2) < scale
        val k = (if (twoRows) min(scale, fitFor(max(n1, n2))) else scale).coerceAtLeast(1f)

        val rowPadding = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp + 48.dp * shiftX,
                end = 16.dp + 48.dp * (1f - shiftX),
                top = 8.dp,
                bottom = 8.dp + 24.dp * shiftY,
            )

        val stationButtons: @Composable () -> Unit = {
            for (i in 0 until 4) {
                val st = slots.getOrNull(i)
                val active = st != null && st.id == activeId
                val tint = when {
                    active && loading -> Color(0xFFFFEB3B)
                    active -> Color(0xFF69F0AE)
                    st == null -> Color(0x88FFFFFF)
                    else -> Color.White
                }
                BarButton(Icons.Filled.Radio, st?.name?.take(8) ?: "prazno", k, tint, st?.iconUrl) { onSlot(i) }
            }
            if (radioOn) TimerButton(timerEndsAt, k, onTimer)
        }
        val otherButtons: @Composable () -> Unit = {
            alarms.forEachIndexed { i, a ->
                BarButton(
                    if (a.enabled) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
                    "%02d:%02d".format(a.hour, a.minute),
                    k,
                    if (a.enabled) Color(0xFF69F0AE) else Color.White,
                ) { onAlarm(i) }
            }
            BarButton(Icons.Filled.Settings, "Nastavitve", k, onClick = onSettings)
        }

        if (twoRows) {
            Column(rowPadding) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) { stationButtons() }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) { otherButtons() }
            }
        } else {
            Row(rowPadding, verticalAlignment = Alignment.CenterVertically) {
                stationButtons()
                Spacer(Modifier.weight(1f))
                otherButtons()
            }
        }
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    k: Float,
    tint: Color = Color.White,
    imageUrl: String? = null,
    onClick: () -> Unit,
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = (6f + 4f * k).dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!imageUrl.isNullOrBlank() && !failed) {
            // Ikona postaje; barvni obroc kaze stanje (zeleno = igra, rumeno = nalaga)
            val shape = RoundedCornerShape(8.dp)
            AsyncImage(
                model = imageUrl,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier
                    .size((36f * k).dp)
                    .clip(shape)
                    .border(2.dp, if (tint == Color.White) Color(0x55FFFFFF) else tint, shape),
            )
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size((32f * k).dp))
        }
        Text(label, color = tint, fontSize = (11f + 3f * k).sp, maxLines = 1)
    }
}

@Composable
private fun TimerButton(endsAt: Long, k: Float, onSelect: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(), endsAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val active = endsAt > 0L
    val remainingMin = if (active) ((endsAt - now + 59_999L) / 60_000L).coerceAtLeast(0L) else 0L

    Box {
        BarButton(
            Icons.Filled.Timer,
            if (active) "$remainingMin min" else "Casovnik",
            k,
            if (active) Color(0xFF69F0AE) else Color.White,
        ) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Izklopljen") }, onClick = { menu = false; onSelect(0) })
            for (m in listOf(15, 30, 45, 60, 90)) {
                DropdownMenuItem(text = { Text("$m min") }, onClick = { menu = false; onSelect(m) })
            }
            DropdownMenuItem(text = { Text("Po meri ...") }, onClick = { menu = false; custom = true })
        }
    }
    if (custom) {
        CustomTimerDialog(onDismiss = { custom = false }, onSet = { custom = false; onSelect(it) })
    }
}

@Composable
private fun CustomTimerDialog(onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf("45") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..720
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Izklop radia cez (minut)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (minutes != null && valid) onSet(minutes) }, enabled = valid) { Text("Nastavi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Preklici") } },
    )
}
