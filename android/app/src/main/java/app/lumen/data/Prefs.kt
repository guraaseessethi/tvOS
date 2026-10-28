package app.lumen.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.store by preferencesDataStore("lumen")

/** Everything the phone remembers (spec 15.2 storage). */
data class Settings(
    val tvIp: String = "",
    val tvName: String = "",
    val tvSerial: String = "",
    val onboarded: Boolean = false,
    val autoOpen: Boolean = true,
    val spokenReplies: Boolean = true,
    val heyZeus: Boolean = false,
    /** Owner choice (28 Oct): Zeus talks on the phone; the TV only shows the chat. */
    val replyOnTv: Boolean = false,
    val voiceName: String = "",
    val lockRemote: Boolean = true,
    val followWeather: Boolean = true,
    val themeKey: String = "starry",
    val overrideKey: String = "",
    val overrideUntil: Long = 0,
    val city: String = "Barrie, Ontario",
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
    val fahrenheit: Boolean = false,
    val pauseUntil: Long = 0,
    val sleepAt: Long = 0,
    val timerAt: Long = 0,
    val timerSeconds: Int = 0,
    val alarmAt: Long = 0,
    val lastAutoOpen: String = "",
    val inputNames: Map<String, String> = emptyMap(),
) {
    val hasTv get() = tvIp.isNotEmpty()
    fun inputName(id: String, fallback: String) = inputNames[id]?.takeIf { it.isNotBlank() } ?: fallback
}

class Prefs(private val context: Context) {
    private object K {
        val tvIp = stringPreferencesKey("tvIp")
        val tvName = stringPreferencesKey("tvName")
        val tvSerial = stringPreferencesKey("tvSerial")
        val onboarded = booleanPreferencesKey("onboarded")
        val autoOpen = booleanPreferencesKey("autoOpen")
        val spokenReplies = booleanPreferencesKey("spokenReplies")
        val heyZeus = booleanPreferencesKey("heyZeus")
        val replyOnTv = booleanPreferencesKey("voiceOnTv")
        val voiceName = stringPreferencesKey("voiceName")
        val lockRemote = booleanPreferencesKey("lockRemote")
        val followWeather = booleanPreferencesKey("followWeather")
        val themeKey = stringPreferencesKey("theme")
        val overrideKey = stringPreferencesKey("overrideTheme")
        val overrideUntil = longPreferencesKey("overrideUntil")
        val city = stringPreferencesKey("city")
        val lat = doublePreferencesKey("lat")
        val lon = doublePreferencesKey("lon")
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val pauseUntil = longPreferencesKey("pauseUntil")
        val sleepAt = longPreferencesKey("sleepAt")
        val timerAt = longPreferencesKey("timerAt")
        val timerSeconds = longPreferencesKey("timerSeconds")
        val alarmAt = longPreferencesKey("alarmAt")
        val lastAutoOpen = stringPreferencesKey("lastAutoOpen")
        val inputNames = stringPreferencesKey("inputNames")
    }

    val settings: Flow<Settings> = context.store.data.map { p -> read(p) }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        val names = runCatching {
            val o = JSONObject(p[K.inputNames] ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        return Settings(
            tvIp = p[K.tvIp] ?: d.tvIp,
            tvName = p[K.tvName] ?: d.tvName,
            tvSerial = p[K.tvSerial] ?: d.tvSerial,
            onboarded = p[K.onboarded] ?: d.onboarded,
            autoOpen = p[K.autoOpen] ?: d.autoOpen,
            spokenReplies = p[K.spokenReplies] ?: d.spokenReplies,
            heyZeus = p[K.heyZeus] ?: d.heyZeus,
            replyOnTv = p[K.replyOnTv] ?: d.replyOnTv,
            voiceName = p[K.voiceName] ?: d.voiceName,
            lockRemote = p[K.lockRemote] ?: d.lockRemote,
            followWeather = p[K.followWeather] ?: d.followWeather,
            themeKey = p[K.themeKey] ?: d.themeKey,
            overrideKey = p[K.overrideKey] ?: d.overrideKey,
            overrideUntil = p[K.overrideUntil] ?: d.overrideUntil,
            city = p[K.city] ?: d.city,
            lat = p[K.lat] ?: d.lat,
            lon = p[K.lon] ?: d.lon,
            fahrenheit = p[K.fahrenheit] ?: d.fahrenheit,
            pauseUntil = p[K.pauseUntil] ?: d.pauseUntil,
            sleepAt = p[K.sleepAt] ?: d.sleepAt,
            timerAt = p[K.timerAt] ?: d.timerAt,
            timerSeconds = (p[K.timerSeconds] ?: 0L).toInt(),
            alarmAt = p[K.alarmAt] ?: d.alarmAt,
            lastAutoOpen = p[K.lastAutoOpen] ?: d.lastAutoOpen,
            inputNames = names,
        )
    }

    suspend fun setTv(ip: String, name: String, serial: String) = context.store.edit {
        it[K.tvIp] = ip; it[K.tvName] = name; it[K.tvSerial] = serial
    }
    suspend fun setOnboarded(v: Boolean) = context.store.edit { it[K.onboarded] = v }
    suspend fun setAutoOpen(v: Boolean) = context.store.edit { it[K.autoOpen] = v }
    suspend fun setSpokenReplies(v: Boolean) = context.store.edit { it[K.spokenReplies] = v }
    suspend fun setHeyZeus(v: Boolean) = context.store.edit { it[K.heyZeus] = v }
    suspend fun setReplyOnTv(v: Boolean) = context.store.edit { it[K.replyOnTv] = v }
    suspend fun setVoiceName(v: String) = context.store.edit { it[K.voiceName] = v }
    suspend fun setLockRemote(v: Boolean) = context.store.edit { it[K.lockRemote] = v }
    suspend fun setFollowWeather(v: Boolean) = context.store.edit { it[K.followWeather] = v }
    suspend fun setTheme(key: String) = context.store.edit { it[K.themeKey] = key }
    suspend fun setOverride(key: String, until: Long) = context.store.edit { it[K.overrideKey] = key; it[K.overrideUntil] = until }
    suspend fun setCity(city: String, lat: Double, lon: Double) = context.store.edit { it[K.city] = city; it[K.lat] = lat; it[K.lon] = lon }
    suspend fun setFahrenheit(v: Boolean) = context.store.edit { it[K.fahrenheit] = v }
    suspend fun setPauseUntil(v: Long) = context.store.edit { it[K.pauseUntil] = v }
    suspend fun setSleepAt(v: Long) = context.store.edit { it[K.sleepAt] = v }
    suspend fun setTimer(at: Long, seconds: Int) = context.store.edit { it[K.timerAt] = at; it[K.timerSeconds] = seconds.toLong() }
    suspend fun setAlarm(at: Long) = context.store.edit { it[K.alarmAt] = at }
    suspend fun setLastAutoOpen(v: String) = context.store.edit { it[K.lastAutoOpen] = v }
    suspend fun setInputName(id: String, name: String) = context.store.edit {
        val o = runCatching { JSONObject(it[K.inputNames] ?: "{}") }.getOrDefault(JSONObject())
        if (name.isBlank()) o.remove(id) else o.put(id, name.trim().take(24))
        it[K.inputNames] = o.toString()
    }
}
