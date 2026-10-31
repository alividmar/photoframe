package si.photoframe

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ---- Moznosti, ki jih uporabnik izbira v nastavitvah ----
enum class ClockStyle { DIGITAL, ANALOG, OFF }
enum class ClockPosition { TOP_LEFT, TOP_RIGHT, CENTER, BOTTOM_LEFT, BOTTOM_RIGHT }
enum class DigitalFont { SANS, SERIF, MONO }
enum class AnalogFace { TICKS, DOTS, NONE }
enum class ImageScale { FIT, FILL }   // FIT = cela slika, ostalo crno; FILL = napolni zaslon (obreze)

/** Vse nastavitve aplikacije na enem mestu. Barve so shranjene kot ARGB (Long). */
data class AppSettings(
    // Slike
    val folderUri: String? = null,
    val intervalSec: Int = 10,
    val shuffle: Boolean = true,
    val imageScale: ImageScale = ImageScale.FIT,
    // Ura
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val clockPosition: ClockPosition = ClockPosition.BOTTOM_RIGHT,
    val clockColor: Long = 0xFFFFFFFF,
    val clockBgColor: Long = 0xFF000000,
    val clockBgAlpha: Float = 0.4f,
    val clockScale: Float = 1.0f,
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val digitalFont: DigitalFont = DigitalFont.SANS,
    val analogFace: AnalogFace = AnalogFace.TICKS,
    // OLED zascita
    val oledMoveClock: Boolean = false,
    val oledMoveBar: Boolean = false,
    val oledMoveMinutes: Int = 3,
    val barAutoHide: Boolean = true,
    val barHideSec: Int = 10,
    // Prehod med slikami
    val fadeMs: Int = 2000,
    val fadeThroughBlack: Boolean = false,
    // Obseg OLED premika ure (0.1 = malo, 1.0 = cez cel zaslon)
    val oledRange: Float = 0.6f,
    // Radio
    val stations: List<Station> = DefaultStations.list,
    val slotIds: List<String> = DefaultStations.slots,
    // Spanje (vecerna zatemnitev ob zagonu radia)
    val nightEnabled: Boolean = true,
    val nightStartMin: Int = 22 * 60,
    val nightEndMin: Int = 6 * 60,
    val nightPhotosOff: Boolean = true,
    val nightBgColor: Long = 0xFF000000,
    val nightBrightness: Float = 0.1f,
    val nightClockAlpha: Float = 0.35f,
    val nightVolumeEnabled: Boolean = false,
    val nightVolumePct: Int = 30,
)

private val Context.dataStore by preferencesDataStore(name = "photoframe_settings")

class SettingsRepository(private val context: Context) {
    private val kFolder = stringPreferencesKey("folder_uri")
    private val kInterval = intPreferencesKey("interval_sec")
    private val kShuffle = booleanPreferencesKey("shuffle")
    private val kImageScale = stringPreferencesKey("image_scale")
    private val kStyle = stringPreferencesKey("clock_style")
    private val kPos = stringPreferencesKey("clock_position")
    private val kColor = longPreferencesKey("clock_color")
    private val kBg = longPreferencesKey("clock_bg_color")
    private val kBgAlpha = floatPreferencesKey("clock_bg_alpha")
    private val kScale = floatPreferencesKey("clock_scale")
    private val k24 = booleanPreferencesKey("use_24h")
    private val kSec = booleanPreferencesKey("show_seconds")
    private val kDate = booleanPreferencesKey("show_date")
    private val kFont = stringPreferencesKey("digital_font")
    private val kFace = stringPreferencesKey("analog_face")
    private val kOledClock = booleanPreferencesKey("oled_move_clock")
    private val kOledBar = booleanPreferencesKey("oled_move_bar")
    private val kOledMin = intPreferencesKey("oled_move_minutes")
    private val kBarHide = booleanPreferencesKey("bar_auto_hide")
    private val kBarSec = intPreferencesKey("bar_hide_sec")
    private val kFadeMs = intPreferencesKey("fade_ms")
    private val kFadeBlack = booleanPreferencesKey("fade_through_black")
    private val kOledRange = floatPreferencesKey("oled_range")
    private val kStations = stringPreferencesKey("stations_json")
    private val kSlots = stringPreferencesKey("slot_ids")
    private val kNightOn = booleanPreferencesKey("night_enabled")
    private val kNightStart = intPreferencesKey("night_start_min")
    private val kNightEnd = intPreferencesKey("night_end_min")
    private val kNightPhotos = booleanPreferencesKey("night_photos_off")
    private val kNightBg = longPreferencesKey("night_bg_color")
    private val kNightBright = floatPreferencesKey("night_brightness")
    private val kNightAlpha = floatPreferencesKey("night_clock_alpha")
    private val kNightVolOn = booleanPreferencesKey("night_volume_enabled")
    private val kNightVol = intPreferencesKey("night_volume_pct")

    val flow: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    /** Prebere trenutne nastavitve, uporabi spremembo in jo shrani. */
    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private inline fun <reified E : Enum<E>> enumOf(raw: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == raw } ?: default

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            folderUri = p[kFolder],
            intervalSec = p[kInterval] ?: d.intervalSec,
            shuffle = p[kShuffle] ?: d.shuffle,
            imageScale = enumOf(p[kImageScale], d.imageScale),
            clockStyle = enumOf(p[kStyle], d.clockStyle),
            clockPosition = enumOf(p[kPos], d.clockPosition),
            clockColor = p[kColor] ?: d.clockColor,
            clockBgColor = p[kBg] ?: d.clockBgColor,
            clockBgAlpha = p[kBgAlpha] ?: d.clockBgAlpha,
            clockScale = p[kScale] ?: d.clockScale,
            use24h = p[k24] ?: d.use24h,
            showSeconds = p[kSec] ?: d.showSeconds,
            showDate = p[kDate] ?: d.showDate,
            digitalFont = enumOf(p[kFont], d.digitalFont),
            analogFace = enumOf(p[kFace], d.analogFace),
            oledMoveClock = p[kOledClock] ?: d.oledMoveClock,
            oledMoveBar = p[kOledBar] ?: d.oledMoveBar,
            oledMoveMinutes = p[kOledMin] ?: d.oledMoveMinutes,
            barAutoHide = p[kBarHide] ?: d.barAutoHide,
            barHideSec = p[kBarSec] ?: d.barHideSec,
            fadeMs = p[kFadeMs] ?: d.fadeMs,
            fadeThroughBlack = p[kFadeBlack] ?: d.fadeThroughBlack,
            oledRange = p[kOledRange] ?: d.oledRange,
            stations = p[kStations]?.let { StationJson.decode(it) } ?: d.stations,
            slotIds = p[kSlots]?.let { SlotCodec.decode(it) } ?: d.slotIds,
            nightEnabled = p[kNightOn] ?: d.nightEnabled,
            nightStartMin = p[kNightStart] ?: d.nightStartMin,
            nightEndMin = p[kNightEnd] ?: d.nightEndMin,
            nightPhotosOff = p[kNightPhotos] ?: d.nightPhotosOff,
            nightBgColor = p[kNightBg] ?: d.nightBgColor,
            nightBrightness = p[kNightBright] ?: d.nightBrightness,
            nightClockAlpha = p[kNightAlpha] ?: d.nightClockAlpha,
            nightVolumeEnabled = p[kNightVolOn] ?: d.nightVolumeEnabled,
            nightVolumePct = p[kNightVol] ?: d.nightVolumePct,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        if (s.folderUri == null) p.remove(kFolder) else p[kFolder] = s.folderUri
        p[kInterval] = s.intervalSec
        p[kShuffle] = s.shuffle
        p[kImageScale] = s.imageScale.name
        p[kStyle] = s.clockStyle.name
        p[kPos] = s.clockPosition.name
        p[kColor] = s.clockColor
        p[kBg] = s.clockBgColor
        p[kBgAlpha] = s.clockBgAlpha
        p[kScale] = s.clockScale
        p[k24] = s.use24h
        p[kSec] = s.showSeconds
        p[kDate] = s.showDate
        p[kFont] = s.digitalFont.name
        p[kFace] = s.analogFace.name
        p[kOledClock] = s.oledMoveClock
        p[kOledBar] = s.oledMoveBar
        p[kOledMin] = s.oledMoveMinutes
        p[kBarHide] = s.barAutoHide
        p[kBarSec] = s.barHideSec
        p[kFadeMs] = s.fadeMs
        p[kFadeBlack] = s.fadeThroughBlack
        p[kOledRange] = s.oledRange
        p[kStations] = StationJson.encode(s.stations)
        p[kSlots] = SlotCodec.encode(s.slotIds)
        p[kNightOn] = s.nightEnabled
        p[kNightStart] = s.nightStartMin
        p[kNightEnd] = s.nightEndMin
        p[kNightPhotos] = s.nightPhotosOff
        p[kNightBg] = s.nightBgColor
        p[kNightBright] = s.nightBrightness
        p[kNightAlpha] = s.nightClockAlpha
        p[kNightVolOn] = s.nightVolumeEnabled
        p[kNightVol] = s.nightVolumePct
    }
}
