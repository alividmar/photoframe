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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.random.Random
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
    var touchCount by remember { mutableIntStateOf(0) }
    var barVisible by remember { mutableStateOf(true) }
    var oledStep by remember { mutableIntStateOf(0) }

    // Ko se spremeni mapa, na novo poiscemo slike
    LaunchedEffect(s.folderUri) {
        photos = s.folderUri?.let { PhotoScanner.scan(context, Uri.parse(it)) } ?: emptyList()
    }

    // Ikone: ob dotiku se pokazejo, po nekaj sekundah se spet skrijejo
    LaunchedEffect(touchCount, showSettings, s.barAutoHide, s.barHideSec) {
        barVisible = true
        if (s.barAutoHide && !showSettings) {
            delay(s.barHideSec * 1000L)
            barVisible = false
        }
    }

    // OLED: vsakih N minut nov polozaj ure in ikon
    LaunchedEffect(s.oledMoveClock, s.oledMoveBar, s.oledMoveMinutes) {
        if (s.oledMoveClock || s.oledMoveBar) {
            while (true) {
                delay(s.oledMoveMinutes * 60_000L)
                oledStep++
            }
        }
    }

    val clockTarget = remember(oledStep) {
        val r = Random(oledStep * 7919L + 13L)
        (r.nextFloat() * 1.8f - 0.9f) to (r.nextFloat() * 1.8f - 0.9f)
    }
    val biasX by animateFloatAsState(clockTarget.first, tween(2500), label = "clockX")
    val biasY by animateFloatAsState(clockTarget.second, tween(2500), label = "clockY")
    val clockAlignment: Alignment =
        if (s.oledMoveClock) BiasAlignment(biasX, biasY) else s.clockPosition.alignment()

    val barShift = remember(oledStep, s.oledMoveBar) {
        if (s.oledMoveBar) {
            val r = Random(oledStep * 104729L + 7L)
            r.nextFloat() to r.nextFloat()
        } else {
            0.5f to 0f
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Opazujemo dotike, ne da bi jih porabili
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) touchCount++
                    }
                }
            },
    ) {
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
                .align(clockAlignment)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
        )

        AnimatedVisibility(
            visible = barVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BottomBar(
                shiftX = barShift.first,
                shiftY = barShift.second,
                onSettings = { showSettings = true },
                onSoon = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            )
        }

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
