package app.lumen.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lumen.Lumen
import kotlinx.coroutines.launch

/** Buttons on the persistent notification (spec 15.5). */
class NotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Lumen.init(context)
        val done = goAsync()
        Lumen.scope.launch {
            try {
                when (intent.action) {
                    PLAY -> Lumen.ip()?.let { Lumen.ecp.keypress(it, "Play") }
                    PAUSE_AUTO -> Lumen.prefs.setPauseUntil(System.currentTimeMillis() + 2 * 3600_000L)
                    RESUME_AUTO -> Lumen.prefs.setPauseUntil(0)
                    CANCEL_SLEEP -> Lumen.setSleep(0)
                }
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val PLAY = "app.lumen.PLAY"
        const val PAUSE_AUTO = "app.lumen.PAUSE_AUTO"
        const val RESUME_AUTO = "app.lumen.RESUME_AUTO"
        const val CANCEL_SLEEP = "app.lumen.CANCEL_SLEEP"
    }
}
