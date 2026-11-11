package com.ivan.grumpweather.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("grump", Context.MODE_PRIVATE)

    var place: Place?
        get() {
            val name = sp.getString("name", null) ?: return null
            val lat = sp.getString("lat", null)?.toDoubleOrNull() ?: return null
            val lon = sp.getString("lon", null)?.toDoubleOrNull() ?: return null
            return Place(
                name = name,
                lat = lat,
                lon = lon,
                region = sp.getString("region", null),
                fromGps = sp.getBoolean("gps", false),
            )
        }
        set(value) {
            val e = sp.edit()
            if (value == null) {
                e.clear()
            } else {
                e.putString("name", value.name)
                    .putString("lat", value.lat.toString())
                    .putString("lon", value.lon.toString())
                    .putString("region", value.region)
                    .putBoolean("gps", value.fromGps)
            }
            e.apply()
        }
}
