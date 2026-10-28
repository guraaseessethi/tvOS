package app.lumen.watcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.lumen.Lumen
import app.lumen.MainActivity
import app.lumen.R
import app.lumen.theme.Painting
import kotlinx.coroutines.launch

/** Spec 15.5 extras: a 2x2 home-screen widget with the current painting, Zeus and play/pause. */
class LumenWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Lumen.init(context)
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            Lumen.scope.launch {
                val p = Lumen.paintingFor(Lumen.loadedSettings(), Lumen.weather.value)
                draw(app, p)
            }
        }

        private fun draw(context: Context, p: Painting) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LumenWidget::class.java))
            if (ids.isEmpty()) return
            val open = PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val talk = PendingIntent.getActivity(
                context, 21,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_LISTEN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val play = PendingIntent.getBroadcast(
                context, 22, Intent(context, NotificationActions::class.java).setAction(NotificationActions.PLAY), PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_lumen).apply {
                setImageViewResource(R.id.widget_painting, p.thumb)
                setTextViewText(R.id.widget_title, p.title)
                setOnClickPendingIntent(R.id.widget_painting, open)
                setOnClickPendingIntent(R.id.widget_zeus, talk)
                setOnClickPendingIntent(R.id.widget_play, play)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
