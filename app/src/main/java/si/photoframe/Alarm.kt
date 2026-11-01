package si.photoframe

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Nastavitve enega alarma. days: bit 0 = ponedeljek ... bit 6 = nedelja; 0 = zazvoni samo enkrat. */
data class AlarmConfig(
    val enabled: Boolean = false,
    val hour: Int = 6,
    val minute: Int = 30,
    val days: Int = 127,
    val useRadio: Boolean = false,
    val stationId: String = "",
    val volumePct: Int = 60,
)

object AlarmCodec {
    val defaults = listOf(AlarmConfig(hour = 6, minute = 30), AlarmConfig(hour = 7, minute = 0))

    fun encode(list: List<AlarmConfig>): String {
        val arr = JSONArray()
        for (a in list) {
            arr.put(
                JSONObject().put("on", a.enabled).put("h", a.hour).put("m", a.minute).put("days", a.days)
                    .put("radio", a.useRadio).put("station", a.stationId).put("vol", a.volumePct),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String): List<AlarmConfig> {
        val out = ArrayList<AlarmConfig>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    AlarmConfig(
                        enabled = o.optBoolean("on", false),
                        hour = o.optInt("h", 6).coerceIn(0, 23),
                        minute = o.optInt("m", 0).coerceIn(0, 59),
                        days = o.optInt("days", 127).coerceIn(0, 127),
                        useRadio = o.optBoolean("radio", false),
                        stationId = o.optString("station"),
                        volumePct = o.optInt("vol", 60).coerceIn(0, 100),
                    ),
                )
            }
        }
        // Vedno tocno dva alarma
        while (out.size < 2) out.add(defaults[out.size])
        return out.take(2)
    }
}

/** Stanje zvonjenja: kateri alarm trenutno zvoni (null = nobeden). */
object AlarmState {
    val ringing = MutableStateFlow<Int?>(null)
}

object AlarmScheduler {
    const val ACTION_FIRE = "si.photoframe.ALARM_FIRE"
    const val EXTRA_INDEX = "index"

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun fireIntent(context: Context, index: Int): PendingIntent = PendingIntent.getBroadcast(
        context, 100 + index,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_INDEX, index),
        flags(),
    )

    /** Naslednji cas zvonjenja (ms) ali null, ce ga ni. */
    fun nextTrigger(cfg: AlarmConfig, now: Long): Long? {
        for (offset in 0..7) {
            val c = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, cfg.hour)
                set(Calendar.MINUTE, cfg.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= now) continue
            val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // ponedeljek = 0 ... nedelja = 6
            if (cfg.days == 0 || ((cfg.days shr dow) and 1) == 1) return c.timeInMillis
        }
        return null
    }

    fun schedule(context: Context, index: Int, cfg: AlarmConfig) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val op = fireIntent(context, index)
        if (!cfg.enabled) {
            am.cancel(op)
            return
        }
        val at = nextTrigger(cfg, System.currentTimeMillis()) ?: return
        val show = PendingIntent.getActivity(context, 200 + index, Intent(context, MainActivity::class.java), flags())
        // setAlarmClock je natancen alarm, za katerega Android ne zahteva posebnega dovoljenja
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
    }

    fun rescheduleAll(context: Context, alarms: List<AlarmConfig>) {
        alarms.forEachIndexed { i, a -> schedule(context, i, a) }
    }

    /** Besedilo tipa "cez 7 h 20 min". */
    fun describeRemaining(at: Long, now: Long): String {
        val totalMin = ((at - now) / 60_000L).coerceAtLeast(0L)
        val d = totalMin / (24 * 60)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return buildString {
            append("cez ")
            if (d > 0) append(d).append(" d ")
            if (d > 0 || h > 0) append(h).append(" h ")
            append(m).append(" min")
        }
    }
}

/** Sprejme sprozitev alarma in zagon naprave (ponoven razpored alarmov). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val action = intent.action
        val index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, -1)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repo = SettingsRepository(app)
                val s = repo.flow.first()
                when (action) {
                    AlarmScheduler.ACTION_FIRE -> {
                        val cfg = s.alarms.getOrNull(index)
                        if (cfg != null && cfg.enabled) {
                            AlarmService.start(app, index, cfg, s.stations.firstOrNull { it.id == cfg.stationId })
                            if (cfg.days == 0) {
                                // Enkratni alarm se po zvonjenju izklopi
                                repo.update { cur ->
                                    cur.copy(alarms = cur.alarms.mapIndexed { i, a -> if (i == index) a.copy(enabled = false) else a })
                                }
                            } else {
                                AlarmScheduler.schedule(app, index, cfg)
                            }
                        }
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                        AlarmScheduler.rescheduleAll(app, s.alarms)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
