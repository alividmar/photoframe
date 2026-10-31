package si.photoframe

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import android.net.Uri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Zaslon naj ostane prizgan, dokler je aplikacija odprta (digitalni okvir)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val repo = SettingsRepository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(repo)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
fun App(repo: SettingsRepository) {
    val loaded by repo.flow.collectAsState(initial = null)
    val s = loaded
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Ko se spremeni mapa, na novo poiscemo slike
    LaunchedEffect(s.folderUri) {
        photos = s.folderUri?.let { PhotoScanner.scan(context, Uri.parse(it)) } ?: emptyList()
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (photos.isNotEmpty()) {
            Slideshow(
                photos = photos,
                intervalSec = s.intervalSec,
                shuffle = s.shuffle,
                cropToFill = s.cropToFill,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            EmptyHint(hasFolder = s.folderUri != null, onOpenSettings = { showSettings = true })
        }

        ClockOverlay(
            s = s,
            modifier = Modifier
                .align(s.clockPosition.alignment())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
        )

        BottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onSettings = { showSettings = true },
            onSoon = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
        )

        if (showSettings) {
            SettingsScreen(
                s = s,
                onChange = { change -> scope.launch { repo.update(change) } },
                onClose = { showSettings = false },
            )
        }
    }
}

@Composable
private fun EmptyHint(hasFolder: Boolean, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasFolder) "V izbrani mapi ni najdenih slik." else "Izberi mapo s slikami.",
                color = Color.White,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) {
                Text("Odpri nastavitve")
            }
        }
    }
}
