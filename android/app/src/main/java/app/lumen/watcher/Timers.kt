package app.lumen.watcher

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.lumen.Lumen
import app.lumen.MainActivity
import app.lumen.R
import app.lumen.zeus.TimeRules
import app.lumen.zeus.VoiceStudio
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Zeus's timers and alarms. They live on the phone (like the sleep timer) so they ring even when
 * the TV is in another app: AlarmManager.setAlarmClock fires on time even in Doze. The TV shows
 * them as pills beside Ask Zeus and says "time's up" out loud when one rings.
 */
object Timers {
    const val TIMER = "timer"
    const val ALARM = "alarm"
    private const val CH_RING = "ring"

    suspend fun setTimer(context: Context, seconds: Int) {
        val at = System.currentTimeMillis() + seconds * 1000L
        Lumen.prefs.setTimer(at, seconds)
        schedule(context, TIMER, at)
        pushToTv()
    }

    suspend fun setAlarm(context: Context, at: Long) {
        Lumen.prefs.setAlarm(at)
        schedule(context, ALARM, at)
        pushToTv()
    }

    suspend fun cancel(context: Context, kind: String) {
        if (kind == TIMER) Lumen.prefs.setTimer(0, 0) else Lumen.prefs.setAlarm(0)
        alarms(context).cancel(pending(context, kind))
        pushToTv()
    }

    /** After a reboot or update: put back whatever is still in the future. */
    suspend fun restore(context: Context) {
        val s = Lumen.loadedSettings()
        val now = System.currentTimeMillis()
        if (s.timerAt > now) schedule(context, TIMER, s.timerAt) else if (s.timerAt > 0) Lumen.prefs.setTimer(0, 0)
        if (s.alarmAt > now) schedule(context, ALARM, s.alarmAt) else if (s.alarmAt > 0) Lumen.prefs.setAlarm(0)
    }

    /** Spec 15.6 style: POST /input?timers=... so Lumen's pills match the phone. */
    suspend fun pushToTv() {
        val ip = Lumen.ip() ?: return
        Lumen.ecp.input(ip, mapOf("timers" to statusJson().toString()))
    }

    /** Epoch seconds for the TV (0 = none). */
    suspend fun statusJson(): JSONObject {
        val s = Lumen.loadedSettings()
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("timer", if (s.timerAt > now) s.timerAt / 1000 else 0)
            .put("alarm", if (s.alarmAt > now) s.alarmAt / 1000 else 0)
            .put("sleep", if (s.sleepAt > now) s.sleepAt / 1000 else 0)
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context, kind: String): PendingIntent = PendingIntent.getBroadcast(
        context, kind.hashCode(),
        Intent(context, TimerReceiver::class.java).setAction("app.lumen.RING").putExtra("kind", kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(context: Context, kind: String, at: Long) {
        val am = alarms(context)
        val show = PendingIntent.getActivity(context, 30, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(context, kind))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, kind))
    }

    /** Rings: an insistent alarm-sound notification on the phone, and Zeus says it on the TV. */
    fun ring(context: Context, kind: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH_RING, "Timers and alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
            })
        }
        Lumen.scope.launch {
            val s = Lumen.loadedSettings()
            val text = if (kind == TIMER) {
                "Time's up. Your ${TimeRules.describe(s.timerSeconds)} timer is done."
            } else {
                "Good morning. It's ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())}, your alarm is going off."
            }
            if (kind == TIMER) Lumen.prefs.setTimer(0, 0) else Lumen.prefs.setAlarm(0)

            val stop = PendingIntent.getBroadcast(
                context, 31, Intent(context, TimerReceiver::class.java).setAction("app.lumen.STOP_RING"), PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CH_RING)
                .setSmallIcon(R.drawable.ic_stat_zeus)
                .setContentTitle(if (kind == TIMER) "Timer done" else "Alarm")
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .addAction(0, "Stop", stop)
                .setDeleteIntent(stop)
                .setTimeoutAfter(60_000)
                .build()
            n.flags = n.flags or Notification.FLAG_INSISTENT
            nm.notify(RING_ID, n)

            // Say it on the TV too, in Zeus's voice (Lumen shows the toast and plays the clip).
            val ip = Lumen.ip() ?: return@launch
            val ring = JSONObject().put("kind", kind).put("text", text)
            val clip = VoiceStudio.render(text, s.voiceName)
            val base = Lumen.bridgeUrl(ip)
            if (clip != null && base != null) ring.put("voice", "$base/voice/$clip")
            Lumen.ecp.input(ip, mapOf("ring" to ring.toString()))
            pushToTv()
        }
    }

    const val RING_ID = 40
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Lumen.init(context)
        when (intent.action) {
            "app.lumen.STOP_RING" -> context.getSystemService(NotificationManager::class.java).cancel(Timers.RING_ID)
            else -> Timers.ring(context, intent.getStringExtra("kind") ?: Timers.TIMER)
        }
    }
}
