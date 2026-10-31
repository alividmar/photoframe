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

/** Vse nastavitve aplikacije na enem mestu. Barve so shranjene kot ARGB (Long). */
data class AppSettings(
    // Slike
    val folderUri: String? = null,
    val intervalSec: Int = 10,
    val shuffle: Boolean = true,
    val cropToFill: Boolean = false,
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
)

private val Context.dataStore by preferencesDataStore(name = "photoframe_settings")

class SettingsRepository(private val context: Context) {
    private val kFolder = stringPreferencesKey("folder_uri")
    private val kInterval = intPreferencesKey("interval_sec")
    private val kShuffle = booleanPreferencesKey("shuffle")
    private val kCrop = booleanPreferencesKey("crop_to_fill")
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
            cropToFill = p[kCrop] ?: d.cropToFill,
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
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        if (s.folderUri == null) p.remove(kFolder) else p[kFolder] = s.folderUri
        p[kInterval] = s.intervalSec
        p[kShuffle] = s.shuffle
        p[kCrop] = s.cropToFill
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
    }
}
