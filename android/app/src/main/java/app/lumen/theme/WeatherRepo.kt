package app.lumen.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Weather(val tempC: Double, val code: Int, val isDay: Boolean, val city: String) {
    val painting get() = Painting.forWeather(code, isDay)

    fun line(fahrenheit: Boolean): String {
        val t = if (fahrenheit) tempC * 9 / 5 + 32 else tempC
        return "${Math.round(t)}° ${describe()} in $city"
    }

    fun describe(): String = when (code) {
        0, 1 -> "clear"
        2 -> "partly cloudy"
        3 -> "cloudy"
        45, 48 -> "foggy"
        in 51..57 -> "drizzle"
        in 61..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storms"
        else -> "outside"
    }
}

/** Open-Meteo, no key needed (spec 8.1). */
class WeatherRepo(private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()) {

    suspend fun geocode(city: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8")
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val first = JSONObject(r.body!!.string()).optJSONArray("results")?.optJSONObject(0) ?: return@use null
                first.getDouble("latitude") to first.getDouble("longitude")
            }
        }.getOrNull()
    }

    suspend fun current(lat: Double, lon: Double, city: String): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day&timezone=auto"
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val c = JSONObject(r.body!!.string()).getJSONObject("current")
                Weather(c.getDouble("temperature_2m"), c.getInt("weather_code"), c.getInt("is_day") == 1, city.substringBefore(","))
            }
        }.getOrNull()
    }
}
