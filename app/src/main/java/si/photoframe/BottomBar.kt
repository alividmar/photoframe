package si.photoframe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spodnja vrstica: 4 radijske postaje (faza 3), 2 alarma (faza 4), nastavitve.
 * Radio in alarm za zdaj samo pokazeta obvestilo.
 */
@Composable
fun BottomBar(modifier: Modifier = Modifier, onSettings: () -> Unit, onSoon: (String) -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..4) {
            BarButton(Icons.Filled.Radio, "$i") { onSoon("Radio $i: pride v fazi 3") }
        }
        Spacer(Modifier.weight(1f))
        for (i in 1..2) {
            BarButton(Icons.Filled.Alarm, "$i") { onSoon("Alarm $i: pride v fazi 4") }
        }
        BarButton(Icons.Filled.Settings, "Nastavitve", onClick = onSettings)
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
