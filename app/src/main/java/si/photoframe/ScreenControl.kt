package si.photoframe

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/** Skrbnik naprave: potreben je samo za pravi izklop zaslona (lockNow). Drugih pravic ne zahteva. */
class ScreenAdminReceiver : DeviceAdminReceiver()

object ScreenAdmin {
    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun component(context: Context) = ComponentName(context, ScreenAdminReceiver::class.java)

    fun isActive(context: Context): Boolean = dpm(context).isAdminActive(component(context))

    /** Ugasne zaslon in zaklene napravo (ce skrbniske pravice niso aktivne, ne naredi nic). */
    fun lockNow(context: Context) {
        if (isActive(context)) runCatching { dpm(context).lockNow() }
    }

    fun activationIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Potrebno samo za ugasnitev zaslona ponoci (zaklep zaslona). Aplikacija drugih pravic ne uporablja.",
        )

    fun remove(context: Context) {
        runCatching { dpm(context).removeActiveAdmin(component(context)) }
    }
}

/** Zbudi zaslon ob koncu nocnega okna (jutranja ura), ker se ob izklopljenem zaslonu notranji casovniki ustavijo. */
object ScreenWake {
    const val ACTION_WAKE = "si.photoframe.SCREEN_WAKE"

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 400, Intent(context, ScreenWakeReceiver::class.java).setAction(ACTION_WAKE), flags(),
    )

    private fun nextOccurrence(minuteOfDay: Int, after: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= after) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(context: Context, s: AppSettings, after: Long = System.currentTimeMillis()) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val op = pending(context)
        if (!(s.nightEnabled && s.nightScreenOff && ScreenAdmin.isActive(context))) {
            am.cancel(op)
            return
        }
        val at = nextOccurrence(s.nightEndMin, after)
        when {
            Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            Build.VERSION.SDK_INT >= 19 -> am.setExact(AlarmManager.RTC_WAKEUP, at, op)
            else -> am.set(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    @Suppress("DEPRECATION")
    fun wakeScreen(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "photoframe:wake",
        )
        lock.acquire(15_000L)
    }
}

class ScreenWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val action = intent.action
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = SettingsRepository(app).flow.first()
                if (action == ScreenWake.ACTION_WAKE && s.nightEnabled && s.nightScreenOff) ScreenWake.wakeScreen(app)
                // Naslednji zbudilnik; minuta zamika, da se isti cas ne ponovi
                ScreenWake.schedule(app, s, System.currentTimeMillis() + 60_000L)
            } finally {
                pending.finish()
            }
        }
    }
}
