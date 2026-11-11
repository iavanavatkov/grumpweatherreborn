package com.ivan.grumpweather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Open-Meteo: бесплатно, без ключей, без регистрации. */
object WeatherApi {

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "GrumpWeatherReborn/1.0 (Android)")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun forecast(lat: Double, lon: Double): Forecast = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
            "precipitation,weather_code,wind_speed_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
            "wind_speed_10m,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "precipitation_probability_max,wind_speed_10m_max" +
            "&wind_speed_unit=ms&timezone=auto&forecast_days=7"
        parseForecast(JSONObject(get(url)))
    }

    suspend fun searchCity(query: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query, "UTF-8")}&count=10&language=ru&format=json"
        val root = JSONObject(get(url))
        val arr = root.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val region = listOf(o.str("admin1"), o.str("country"))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
            Place(
                name = o.getString("name"),
                lat = o.getDouble("latitude"),
                lon = o.getDouble("longitude"),
                region = region.ifBlank { null },
            )
        }
    }

    private fun JSONObject.str(key: String): String =
        if (has(key) && !isNull(key)) optString(key, "") else ""

    private fun JSONArray.dbl(i: Int, def: Double = 0.0): Double =
        if (isNull(i)) def else optDouble(i, def)

    private fun JSONArray.int(i: Int, def: Int = 0): Int =
        if (isNull(i)) def else optDouble(i, def.toDouble()).roundToInt()

    private fun JSONObject.dbl(key: String, def: Double = 0.0): Double =
        if (!has(key) || isNull(key)) def else optDouble(key, def)

    private fun parseForecast(root: JSONObject): Forecast {
        val c = root.getJSONObject("current")
        val temp = c.dbl("temperature_2m")
        val wind = c.dbl("wind_speed_10m")
        val current = CurrentWeather(
            time = c.getString("time"),
            temp = temp,
            feelsLike = c.dbl("apparent_temperature", temp),
            humidity = c.dbl("relative_humidity_2m", 50.0).roundToInt(),
            windMs = wind,
            gustMs = c.dbl("wind_gusts_10m", wind),
            precipitation = c.dbl("precipitation"),
            code = c.dbl("weather_code").roundToInt(),
            isDay = c.dbl("is_day", 1.0) >= 0.5,
        )

        val h = root.getJSONObject("hourly")
        val hTime = h.getJSONArray("time")
        val hTemp = h.getJSONArray("temperature_2m")
        val hFeels = h.getJSONArray("apparent_temperature")
        val hHum = h.getJSONArray("relative_humidity_2m")
        val hCode = h.getJSONArray("weather_code")
        val hWind = h.getJSONArray("wind_speed_10m")
        val hProb = h.getJSONArray("precipitation_probability")

        val allHours = (0 until hTime.length()).map { i ->
            val t = hTemp.dbl(i)
            HourWeather(
                time = hTime.getString(i),
                temp = t,
                feelsLike = hFeels.dbl(i, t),
                humidity = hHum.int(i, 50),
                code = hCode.int(i),
                windMs = hWind.dbl(i),
                precipProb = hProb.int(i),
            )
        }
        // Время у Open-Meteo локальное и в формате "2026-11-11T13:00" — сравниваем как строки.
        val nowHour = current.time.take(13)
        val start = allHours.indexOfFirst { it.time.take(13) >= nowHour }.coerceAtLeast(0)
        val hours = allHours.drop(start).take(24)

        val d = root.getJSONObject("daily")
        val dTime = d.getJSONArray("time")
        val dCode = d.getJSONArray("weather_code")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dSum = d.getJSONArray("precipitation_sum")
        val dProb = d.getJSONArray("precipitation_probability_max")
        val dWind = d.getJSONArray("wind_speed_10m_max")

        val days = (0 until dTime.length()).map { i ->
            val date = dTime.getString(i)
            // Дневной влажности в daily нет — усредняем почасовую с 9 до 18.
            val dayHum = allHours
                .filter { it.time.startsWith(date) && it.time.substring(11, 13).toInt() in 9..18 }
                .map { it.humidity }
                .takeIf { it.isNotEmpty() }
                ?.average()?.roundToInt() ?: 50
            DayWeather(
                date = date,
                code = dCode.int(i),
                tMax = dMax.dbl(i),
                tMin = dMin.dbl(i),
                precipSum = dSum.dbl(i),
                precipProb = dProb.int(i),
                windMax = dWind.dbl(i),
                humidity = dayHum,
            )
        }

        return Forecast(current, hours, days)
    }
}
