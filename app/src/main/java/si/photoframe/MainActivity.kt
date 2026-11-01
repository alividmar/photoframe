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
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.alpha
import androidx.core.content.ContextCompat
import java.time.LocalTime
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import coil.Coil
import coil.ImageLoader
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import android.net.Uri

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ALARM = "alarm"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_ALARM, false) == true) allowOverLockScreen(true)
        // Zaslon naj ostane prizgan, dokler je aplikacija odprta (digitalni okvir)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Nalaganje slik prek https tudi na starih napravah (ikone postaj)
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext).okHttpClient { SecureHttp.client(applicationContext) }.build(),
        )

        val repo = SettingsRepository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(repo)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ALARM, false)) allowOverLockScreen(true)
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

/** Ali je trenutni cas v nocnem oknu (okno lahko prehaja cez polnoc). */
fun inNightWindow(startMin: Int, endMin: Int): Boolean {
    val now = LocalTime.now()
    val m = now.hour * 60 + now.minute
    return when {
        startMin == endMin -> false
        startMin < endMin -> m in startMin until endMin
        else -> m >= startMin || m < endMin
    }
}

/** Dovoli ali prepove prikaz nad zaklenjenim zaslonom (uporabimo samo med alarmom). */
@Suppress("DEPRECATION")
fun Activity.allowOverLockScreen(on: Boolean) {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(on)
        setTurnScreenOn(on)
    } else {
        val f = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        if (on) window.addFlags(f) else window.clearFlags(f)
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
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var touchCount by remember { mutableIntStateOf(0) }
    var barVisible by remember { mutableStateOf(true) }
    var oledStep by remember { mutableIntStateOf(0) }
    var nightActive by rememberSaveable { mutableStateOf(false) }

    val radioStatus by RadioState.status.collectAsState()
    val radioStation by RadioState.station.collectAsState()
    val timerEndsAt by RadioState.timerEndsAt.collectAsState()
    val ringing by AlarmState.ringing.collectAsState()
    val radioOn = radioStatus == RadioStatus.LOADING || radioStatus == RadioStatus.PLAYING
    // Zaslon se lahko ugasne (sistem ga ugasne po casu mirovanja): ponoci ali kadar koli igra radio, ce uporabnik tako zeli
    val screenMayOff = (nightActive && s.nightScreenOff) || (radioOn && s.radioScreenOff && ringing == null)
    val hideClock = (nightActive && s.nightHideClock) || (radioOn && s.radioHideClock)
    val slotStations = List(4) { i ->
        s.slotIds.getOrNull(i)?.let { id -> s.stations.firstOrNull { it.id == id } }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    fun playStation(st: Station) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RadioService.play(context, st, s.defaultTimerMin)
    }

    // Ko se spremeni mapa, na novo poiscemo slike
    LaunchedEffect(s.folderUri) {
        photos = s.folderUri?.let { PhotoScanner.scan(context, Uri.parse(it)) } ?: emptyList()
    }

    // Alarmi: ob vsaki spremembi nastavitev ponovno razporedimo
    LaunchedEffect(s.alarms) {
        AlarmScheduler.rescheduleAll(context, s.alarms)
    }

    // Med zvonjenjem alarma smemo prikaz nad zaklenjenim zaslonom; po koncu spet ne
    LaunchedEffect(ringing != null) {
        activity?.allowOverLockScreen(ringing != null)
    }

    // Napaka radia (povezava ni uspela niti po vec poskusih)
    LaunchedEffect(radioStatus) {
        if (radioStatus == RadioStatus.ERROR) {
            val detail = RadioState.error.value
            val msg = "Radia ni mogoce predvajati" + if (detail.isNullOrBlank()) "." else ": $detail"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Spanje: ce radio zacne igrati v nocnem oknu, vklopimo zatemnitev. Konec: jutranja ura, alarm,
    // ali (ce uporabnik tako zeli) takoj ob ustavitvi radia.
    LaunchedEffect(radioOn, ringing != null, s.nightEnabled, s.nightStartMin, s.nightEndMin, s.nightStayUntilMorning) {
        if (ringing != null || !s.nightEnabled) {
            nightActive = false
            return@LaunchedEffect
        }
        if (radioOn && !nightActive && inNightWindow(s.nightStartMin, s.nightEndMin)) nightActive = true
        if (!radioOn && !s.nightStayUntilMorning) {
            nightActive = false
            return@LaunchedEffect
        }
        while (true) {
            delay(30_000L)
            if (nightActive && !inNightWindow(s.nightStartMin, s.nightEndMin)) nightActive = false
        }
    }

    // Glasnost: ob vklopu nocnega nacina enkrat nastavimo sistemsko glasnost predvajanja
    LaunchedEffect(nightActive) {
        if (nightActive && s.nightVolumeEnabled) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val level = (max * s.nightVolumePct / 100f).toInt().coerceIn(0, max)
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
        }
    }

    // Svetlost zaslona ponoci; po zelji pustimo sistemu, da zaslon ugasne
    LaunchedEffect(nightActive, s.nightBrightness, screenMayOff) {
        val w = activity?.window ?: return@LaunchedEffect
        val lp = w.attributes
        lp.screenBrightness =
            if (nightActive) s.nightBrightness.coerceIn(0.01f, 1f) else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        w.attributes = lp
        if (screenMayOff) {
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Ikone: ob dotiku se pokazejo, po nekaj sekundah se spet skrijejo (ponoci vedno)
    val autoHide = s.barAutoHide || nightActive
    LaunchedEffect(touchCount, showSettings, autoHide, s.barHideSec) {
        barVisible = true
        if (autoHide && !showSettings) {
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

    val clockTarget = remember(oledStep, s.clockPosition, s.oledMoveClock, s.oledRange) {
        val (bx, by) = s.clockPosition.bias()
        if (s.oledMoveClock) {
            val r = Random(oledStep * 7919L + 13L)
            wander(bx, r.nextFloat(), s.oledRange) to wander(by, r.nextFloat(), s.oledRange)
        } else {
            bx to by
        }
    }
    val biasX by animateFloatAsState(clockTarget.first, tween(2500), label = "clockX")
    val biasY by animateFloatAsState(clockTarget.second, tween(2500), label = "clockY")
    val clockAlignment: Alignment = BiasAlignment(biasX, biasY)

    val barShift = remember(oledStep, s.oledMoveBar) {
        if (s.oledMoveBar) {
            val r = Random(oledStep * 104729L + 7L)
            r.nextFloat() to r.nextFloat()
        } else {
            0.5f to 0f
        }
    }

    val photosOff = nightActive && s.nightPhotosOff
    val dimAlpha = if (nightActive) s.nightClockAlpha else 1f

    BackHandler(enabled = showSettings) { showSettings = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (photosOff) Color(s.nightBgColor) else Color.Black)
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
        if (!photosOff) {
            if (photos.isNotEmpty()) {
                Slideshow(
                    photos = photos,
                    intervalSec = s.intervalSec,
                    shuffle = s.shuffle,
                    imageScale = s.imageScale,
                    fadeMs = s.fadeMs,
                    fadeThroughBlack = s.fadeThroughBlack,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EmptyHint(hasFolder = s.folderUri != null, onOpenSettings = { showSettings = true })
            }
        }

        if (!hideClock) {
            ClockOverlay(
                s = s,
                modifier = Modifier
                    .align(clockAlignment)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp)
                    .alpha(dimAlpha),
            )
        }

        AnimatedVisibility(
            visible = barVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BottomBar(
                modifier = Modifier.alpha(dimAlpha),
                shiftX = barShift.first,
                shiftY = barShift.second,
                scale = s.barScale,
                slots = slotStations,
                activeId = if (radioOn) radioStation?.id else null,
                loading = radioStatus == RadioStatus.LOADING,
                radioOn = radioOn,
                timerEndsAt = timerEndsAt,
                alarms = s.alarms,
                onSlot = { i ->
                    val st = slotStations[i]
                    when {
                        st == null ->
                            Toast.makeText(context, "Ikona je prazna. Postajo izberes v Nastavitvah.", Toast.LENGTH_SHORT).show()
                        radioOn && radioStation?.id == st.id -> RadioService.stop(context)
                        else -> playStation(st)
                    }
                },
                onTimer = { minutes -> RadioService.setTimer(context, minutes) },
                onAlarm = { i ->
                    val cur = s.alarms.getOrNull(i)
                    if (cur != null) {
                        val turnOn = !cur.enabled
                        scope.launch {
                            repo.update { c ->
                                c.copy(alarms = c.alarms.mapIndexed { idx, a -> if (idx == i) a.copy(enabled = turnOn) else a })
                            }
                        }
                        val msg = if (turnOn) {
                            val now = System.currentTimeMillis()
                            val at = AlarmScheduler.nextTrigger(cur, now)
                            "Alarm ${i + 1} vklopljen: %02d:%02d".format(cur.hour, cur.minute) +
                                (if (at != null) " (" + AlarmScheduler.describeRemaining(at, now) + ")" else "")
                        } else {
                            "Alarm ${i + 1} izklopljen"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                onSettings = { showSettings = true },
            )
        }

        if (showSettings) {
            SettingsScreen(
                s = s,
                onChange = { change -> scope.launch { repo.update(change) } },
                onClose = { showSettings = false },
            )
        }

        // Zvonjenje alarma je vedno na vrhu
        val ringIndex = ringing
        if (ringIndex != null) {
            AlarmOverlay(index = ringIndex, cfg = s.alarms.getOrNull(ringIndex), onStop = { AlarmService.stop(context) })
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
