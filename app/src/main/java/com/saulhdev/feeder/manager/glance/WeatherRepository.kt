/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.manager.glance

import android.util.Log
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * A place, and the current conditions there.
 *
 * Sunrises and sunsets are kept as the ISO local date-times the service
 * returned, for today and tomorrow, rather than as a single formatted "HH:mm".
 * Two reasons: after dark, today's sunset is in the past and showing it is
 * stale — the useful number is the next sunrise — and whether it is currently
 * day has to be derived at read time, because the response is cached for half
 * an hour and a cached is_day flag would be wrong for up to that long either
 * side of dusk.
 */
data class GlanceWeather(
    val place: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val utcOffsetSeconds: Int,
    val sunrises: List<String>,
    val sunsets: List<String>,
    val fetchedAt: Long,
    /**
     * Today's highest chance of rain, as a percentage, or null when the
     * forecast did not carry one.
     *
     * The day's maximum rather than the value for this hour: the useful
     * question a glance answers is "do I need a coat today", and an hourly
     * figure read at 8am says nothing about the afternoon.
     */
    val precipitationChance: Int? = null,
) {
    /** Now, in the place's own local time — not the device's. */
    private val localNow: LocalDateTime
        get() = Instant.ofEpochMilli(System.currentTimeMillis())
            .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
            .toLocalDateTime()

    private fun nextAfterNow(times: List<String>): LocalDateTime? {
        val now = localNow
        return times.mapNotNull { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            .firstOrNull { it.isAfter(now) }
    }

    val nextSunrise: LocalDateTime? get() = nextAfterNow(sunrises)
    val nextSunset: LocalDateTime? get() = nextAfterNow(sunsets)

    /**
     * Daylight if the next sunset falls before the next sunrise. Comparing the
     * two upcoming events settles it without needing to know the date, and is
     * right at 03:00 as well as at 21:00.
     */
    val isDay: Boolean
        get() {
            val sunset = nextSunset ?: return true
            val sunrise = nextSunrise ?: return true
            return sunset.isBefore(sunrise)
        }
}

data class GlancePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Weather and sunrise/sunset, from Open-Meteo.
 *
 * Open-Meteo is used because it needs no API key and no account, does not
 * require Play Services, and states that it does not track users — which is
 * what makes it compatible with the project's "no analytics, no telemetry" rule.
 * Its forecast endpoint returns the current conditions *and* today's sunrise and
 * sunset in one response, so both chips cost a single request.
 *
 * Location comes from a place the user searched for, not from the device: no
 * runtime permission is requested and no coordinate leaves the app except the
 * one the user chose themselves. The coordinates are rounded before being sent,
 * which is plenty for weather at city scale and avoids handing a third party a
 * precise position.
 */
class WeatherRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .onlyPublicHttps()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Looks up places by name. Returns an empty list rather than throwing. */
    suspend fun searchPlaces(query: String): List<GlancePlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}" +
                    "&count=8&format=json"
            val body = get(url, "place search") ?: return@withContext emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).map { i ->
                val o = results.getJSONObject(i)
                val parts = listOfNotNull(
                    o.optString("name").takeIf { it.isNotBlank() },
                    o.optString("admin1").takeIf { it.isNotBlank() },
                    o.optString("country").takeIf { it.isNotBlank() },
                )
                GlancePlace(
                    name = parts.joinToString(", "),
                    latitude = o.optDouble("latitude"),
                    longitude = o.optDouble("longitude"),
                )
            }
        }.getOrElse {
            Log.w(TAG, "Place search failed", it)
            emptyList()
        }
    }

    /** Current conditions plus today's sunrise and sunset. Null on any failure. */
    suspend fun fetch(place: GlancePlace): GlanceWeather? = withContext(Dispatchers.IO) {
        runCatching {
            // Two decimal places is about a kilometre — enough for city weather,
            // and deliberately not a precise position.
            val lat = String.format(java.util.Locale.US, "%.2f", place.latitude)
            val lon = String.format(java.util.Locale.US, "%.2f", place.longitude)
            val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=sunrise,sunset,precipitation_probability_max" +
                    // Two days, so that after dark there is still a sunrise
                    // ahead of "now" to show.
                    "&timezone=auto&forecast_days=2"
            val body = get(url, "forecast") ?: return@withContext null
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val daily = root.optJSONObject("daily")
            fun times(key: String): List<String> {
                val arr = daily?.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            }
            GlanceWeather(
                place = place.name,
                temperatureC = current.optDouble("temperature_2m"),
                weatherCode = current.optInt("weather_code", -1),
                utcOffsetSeconds = root.optInt("utc_offset_seconds", 0),
                sunrises = times("sunrise"),
                sunsets = times("sunset"),
                fetchedAt = System.currentTimeMillis(),
                // Index 0 is today. Open-Meteo sends null for days it has no
                // probability for, which optInt would quietly turn into 0 —
                // "no data" and "no chance of rain" are not the same answer.
                precipitationChance = daily?.optJSONArray("precipitation_probability_max")
                    ?.let { if (it.isNull(0)) null else it.optInt(0) },
            )
        }.getOrElse {
            Log.w(TAG, "Weather fetch failed", it)
            null
        }
    }

    /** @param what which request this is, for the log: never the address. */
    private fun get(url: String, what: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Whisper RSS reader")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // Which request, not the address: its query is the
                // reader's location, or the place they typed.
                Log.w(TAG, "HTTP ${response.code} for the $what")
                return null
            }
            return response.body.string()
        }
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}

/** A WMO weather code, described for the UI. */
data class WeatherLook(
    @androidx.annotation.DrawableRes val iconRes: Int,
    @androidx.annotation.StringRes val labelRes: Int,
)

/**
 * Open-Meteo's WMO weather code, as a Material Symbol and a label.
 *
 * The codes are grouped rather than enumerated one by one: the distinction
 * between "slight" and "moderate" drizzle is not worth a chip's width.
 */
fun weatherLook(code: Int, isDay: Boolean = true): WeatherLook = when (code) {
    // Only the three conditions whose day artwork contains a sun have a night
    // counterpart. Overcast, fog, snow and storm are drawn without one, so a
    // separate night version would be the same picture.
    0          -> if (isDay) WeatherLook(R.drawable.ic_glance_weather_clear, R.string.weather_clear)
                  else WeatherLook(R.drawable.ic_glance_weather_clear_night, R.string.weather_clear)
    1, 2       -> if (isDay) WeatherLook(R.drawable.ic_glance_weather_partly_cloudy, R.string.weather_partly_cloudy)
                  else WeatherLook(R.drawable.ic_glance_weather_partly_cloudy_night, R.string.weather_partly_cloudy)
    3          -> WeatherLook(R.drawable.ic_glance_weather_cloudy, R.string.weather_cloudy)
    45, 48     -> WeatherLook(R.drawable.ic_glance_weather_fog, R.string.weather_fog)
    in 51..57  -> WeatherLook(rainIcon(isDay), R.string.weather_drizzle)
    in 61..67  -> WeatherLook(rainIcon(isDay), R.string.weather_rain)
    in 71..77  -> WeatherLook(R.drawable.ic_glance_weather_snow, R.string.weather_snow)
    in 80..82  -> WeatherLook(rainIcon(isDay), R.string.weather_showers)
    in 85..86  -> WeatherLook(R.drawable.ic_glance_weather_snow, R.string.weather_snow_showers)
    in 95..99  -> WeatherLook(R.drawable.ic_glance_weather_storm, R.string.weather_thunderstorm)
    else       -> WeatherLook(R.drawable.ic_glance_weather_unknown, R.string.weather_unknown)
}

private fun rainIcon(isDay: Boolean) =
    if (isDay) R.drawable.ic_glance_weather_rain else R.drawable.ic_glance_weather_rain_night
