package app.lumen.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lumen.Lumen
import kotlinx.coroutines.launch

/** Starts the watcher after a reboot or an app update (spec 15.2). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Lumen.init(context)
        val done = goAsync()
        Lumen.scope.launch {
            try {
                val s = Lumen.loadedSettings()
                if (s.onboarded && s.hasTv) TvWatcherService.start(context)
            } finally {
                done.finish()
            }
        }
    }
}
