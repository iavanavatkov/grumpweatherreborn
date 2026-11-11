package com.ivan.grumpweather.data

data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    val region: String? = null,
    val fromGps: Boolean = false,
)

data class CurrentWeather(
    val time: String,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windMs: Double,
    val gustMs: Double,
    val precipitation: Double,
    val code: Int,
    val isDay: Boolean,
)

data class HourWeather(
    val time: String,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val code: Int,
    val windMs: Double,
    val precipProb: Int,
)

data class DayWeather(
    val date: String,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val precipSum: Double,
    val precipProb: Int,
    val windMax: Double,
    val humidity: Int,
)

data class Forecast(
    val current: CurrentWeather,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
)
