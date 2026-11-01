package si.photoframe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Predvaja radijski tok v ozadju (tudi ko je zaslon ugasnjen), kot "foreground" servis z obvestilom.
 * Ob prekinitvi povezave poskusi samodejno znova. Casovnik izklopa tece tukaj, zato deluje tudi,
 * ce aplikacijo zapres.
 */
@OptIn(UnstableApi::class)
class RadioService : Service() {

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val stopRunnable = Runnable { finish(RadioStatus.STOPPED) }
    private val retryRunnable = Runnable { startPlayback() }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Radio", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Radio"
                val id = intent.getStringExtra(EXTRA_ID) ?: ""
                // Obvestilo moramo pokazati takoj, sicer Android servis ubije
                startForegroundCompat(buildNotification(name, "Povezujem ..."))
                if (url.isBlank()) {
                    finish(RadioStatus.ERROR)
                } else {
                    RadioState.station.value = Station(id, name, url)
                    RadioState.status.value = RadioStatus.LOADING
                    retryCount = 0
                    handler.removeCallbacks(retryRunnable)
                    startPlayback()
                }
            }
            ACTION_STOP -> finish(RadioStatus.STOPPED)
            ACTION_TIMER -> applyTimer(intent.getIntExtra(EXTRA_MIN, 0))
        }
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        val st = RadioState.station.value ?: return
        val p = player ?: createPlayer().also { player = it }
        p.setMediaItem(MediaItem.fromUri(st.url))
        p.prepare()
        p.playWhenReady = true
    }

    private fun createPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)   // http -> https preusmeritve
            .setUserAgent("PhotoFrame/0.4")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val sources = DefaultMediaSourceFactory(this).setDataSourceFactory(http)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        retryCount = 0
                        RadioState.status.value = RadioStatus.PLAYING
                        updateNotification("Predvaja")
                    }
                    Player.STATE_BUFFERING -> {
                        RadioState.status.value = RadioStatus.LOADING
                        updateNotification("Nalagam ...")
                    }
                    Player.STATE_ENDED -> scheduleRetry()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                scheduleRetry()
            }
        })
        return p
    }

    private fun scheduleRetry() {
        retryCount++
        if (retryCount > MAX_RETRIES) {
            finish(RadioStatus.ERROR)
            return
        }
        RadioState.status.value = RadioStatus.LOADING
        updateNotification("Ponovno se povezujem ...")
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, 3_000L * retryCount)
    }

    private fun applyTimer(minutes: Int) {
        handler.removeCallbacks(stopRunnable)
        if (minutes > 0) {
            handler.postDelayed(stopRunnable, minutes * 60_000L)
            RadioState.timerEndsAt.value = System.currentTimeMillis() + minutes * 60_000L
        } else {
            RadioState.timerEndsAt.value = 0L
        }
    }

    @Suppress("DEPRECATION")
    private fun finish(result: RadioStatus) {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        RadioState.timerEndsAt.value = 0L
        RadioState.status.value = result
        if (result == RadioStatus.STOPPED) RadioState.station.value = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        if (RadioState.status.value != RadioStatus.ERROR) {
            RadioState.status.value = RadioStatus.STOPPED
            RadioState.station.value = null
        }
        RadioState.timerEndsAt.value = 0L
        super.onDestroy()
    }

    // ---- Obvestilo ----

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun buildNotification(name: String, text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RadioService::class.java).setAction(ACTION_STOP), pendingFlags(),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle(name)
            .setContentText(text)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Ustavi", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val name = RadioState.station.value?.name ?: "Radio"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(name, text))
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val ACTION_PLAY = "si.photoframe.PLAY"
        private const val ACTION_STOP = "si.photoframe.STOP"
        private const val ACTION_TIMER = "si.photoframe.TIMER"
        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_URL = "url"
        private const val EXTRA_MIN = "min"
        private const val CHANNEL_ID = "radio"
        private const val NOTIF_ID = 1001
        private const val MAX_RETRIES = 6

        private fun running(): Boolean =
            RadioState.status.value == RadioStatus.LOADING || RadioState.status.value == RadioStatus.PLAYING

        fun play(context: Context, st: Station) {
            val i = Intent(context, RadioService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_ID, st.id)
                .putExtra(EXTRA_NAME, st.name)
                .putExtra(EXTRA_URL, st.url)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            if (running()) context.startService(Intent(context, RadioService::class.java).setAction(ACTION_STOP))
        }

        /** minutes = 0 izklopi casovnik. */
        fun setTimer(context: Context, minutes: Int) {
            if (running()) {
                context.startService(
                    Intent(context, RadioService::class.java).setAction(ACTION_TIMER).putExtra(EXTRA_MIN, minutes),
                )
            }
        }
    }
}
