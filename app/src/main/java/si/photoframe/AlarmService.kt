package si.photoframe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Zvonjenje alarma: ton ali radio, z nastavljeno glasnostjo.
 * Ce radio v 30 sekundah ne zacne igrati (npr. ni omrezja), se samodejno vklopi ton, da te alarm vseeno zbudi.
 * Varnostno se alarm po 30 minutah sam ustavi.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var startedRadio = false
    private var volume = 60

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Alarm", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> startAlarm(intent)
            ACTION_STOP -> stopAlarm()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(intent: Intent) {
        val index = intent.getIntExtra(EXTRA_INDEX, 0)
        val useRadio = intent.getBooleanExtra(EXTRA_RADIO, false)
        volume = intent.getIntExtra(EXTRA_VOLUME, 60)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Radio"
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val timeText = intent.getStringExtra(EXTRA_TIME).orEmpty()

        startForegroundCompat(buildNotification(index, timeText))
        AlarmState.ringing.value = index

        if (useRadio && url.isNotBlank()) {
            setVolume(AudioManager.STREAM_MUSIC)
            startedRadio = true
            RadioService.play(this, Station(id, name, url), 0)
            // Zasilni ton, ce radio ne zacne igrati
            handler.postDelayed({
                if (RadioState.status.value != RadioStatus.PLAYING) {
                    RadioService.stop(this)
                    startedRadio = false
                    startTone()
                }
            }, 30_000L)
        } else {
            startTone()
        }
        handler.postDelayed({ stopAlarm() }, 30 * 60_000L)
    }

    private fun setVolume(stream: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream)
        val level = (max * volume / 100f).toInt().coerceIn(if (volume > 0) 1 else 0, max)
        runCatching { am.setStreamVolume(stream, level, 0) }
    }

    private fun startTone() {
        if (player != null) return
        setVolume(AudioManager.STREAM_ALARM)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
        if (startedRadio) RadioService.stop(this)
        startedRadio = false
        AlarmState.ringing.value = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
        AlarmState.ringing.value = null
        super.onDestroy()
    }

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun buildNotification(index: Int, timeText: String): Notification {
        val open = PendingIntent.getActivity(
            this, 300,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ALARM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags(),
        )
        val stop = PendingIntent.getService(
            this, 301, Intent(this, AlarmService::class.java).setAction(ACTION_STOP), flags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle("Alarm ${index + 1}")
            .setContentText(timeText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(android.R.drawable.ic_media_pause, "Ustavi", stop)
            .build()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val ACTION_START = "si.photoframe.ALARM_START"
        private const val ACTION_STOP = "si.photoframe.ALARM_STOP"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_RADIO = "radio"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TIME = "time"
        private const val CHANNEL_ID = "alarm"
        private const val NOTIF_ID = 1002

        fun start(context: Context, index: Int, cfg: AlarmConfig, station: Station?) {
            val i = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_RADIO, cfg.useRadio && station != null)
                .putExtra(EXTRA_VOLUME, cfg.volumePct)
                .putExtra(EXTRA_TIME, "%02d:%02d".format(cfg.hour, cfg.minute))
            if (station != null) {
                i.putExtra(EXTRA_URL, station.url).putExtra(EXTRA_NAME, station.name).putExtra(EXTRA_ID, station.id)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            if (AlarmState.ringing.value != null) {
                context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
