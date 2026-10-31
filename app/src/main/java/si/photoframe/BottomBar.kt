package si.photoframe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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

/**
 * Spodnja vrstica: 4 radijske postaje, casovnik izklopa (le ko radio igra), 2 alarma (faza 4), nastavitve.
 */
@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    shiftX: Float = 0.5f,   // 0..1: vodoravni premik ikon (OLED)
    shiftY: Float = 0f,     // 0..1: navpicni premik ikon (OLED)
    slots: List<Station?>,
    activeId: String?,
    loading: Boolean,
    radioOn: Boolean,
    timerEndsAt: Long,
    onSlot: (Int) -> Unit,
    onTimer: (Int) -> Unit,
    onSettings: () -> Unit,
    onAlarmSoon: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .navigationBarsPadding()
            .padding(
                start = 16.dp + 48.dp * shiftX,
                end = 16.dp + 48.dp * (1f - shiftX),
                top = 8.dp,
                bottom = 8.dp + 24.dp * shiftY,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 4) {
            val st = slots.getOrNull(i)
            val active = st != null && st.id == activeId
            val tint = when {
                active && loading -> Color(0xFFFFEB3B)
                active -> Color(0xFF69F0AE)
                st == null -> Color(0x88FFFFFF)
                else -> Color.White
            }
            BarButton(Icons.Filled.Radio, st?.name?.take(8) ?: "prazno", tint, st?.iconUrl) { onSlot(i) }
        }
        if (radioOn) TimerButton(timerEndsAt, onTimer)
        Spacer(Modifier.weight(1f))
        for (i in 1..2) {
            BarButton(Icons.Filled.Alarm, "$i") { onAlarmSoon("Alarm $i: pride v fazi 4") }
        }
        BarButton(Icons.Filled.Settings, "Nastavitve", onClick = onSettings)
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    imageUrl: String? = null,
    onClick: () -> Unit,
) {
    var failed by remember(imageUrl) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
                    .size(36.dp)
                    .clip(shape)
                    .border(2.dp, if (tint == Color.White) Color(0x55FFFFFF) else tint, shape),
            )
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(32.dp))
        }
        Text(label, color = tint, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun TimerButton(endsAt: Long, onSelect: (Int) -> Unit) {
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
