package app.lumen.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Firebase App Check, so Zeus keeps working when App Check is enforced for AI Logic.
 * The debug provider makes a secret once per install; registering it in the console
 * (App Check > Apps > Lumen > Manage debug tokens) lets this phone through.
 */
object AppCheck {
    fun install(context: Context) {
        if (!Auth.ready(context)) return
        runCatching {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            // Fetch a token now, so Zeus's first question doesn't wait for it.
            appCheck.getAppCheckToken(false)
        }
    }

    /**
     * The debug secret the provider saved, or null before Zeus has been used once. Read from the
     * provider's own preferences (the same value it prints to logcat as "debug secret").
     */
    fun debugToken(context: Context): String? {
        if (!Auth.ready(context)) return null
        val key = runCatching { FirebaseApp.getInstance().persistenceKey }.getOrNull() ?: return null
        val prefs = context.getSharedPreferences("com.google.firebase.appcheck.debug.store.$key", Context.MODE_PRIVATE)
        return prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
    }
}
