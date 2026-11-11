package com.ivan.grumpweather

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.grumpweather.data.Forecast
import com.ivan.grumpweather.data.LocationHelper
import com.ivan.grumpweather.data.Place
import com.ivan.grumpweather.data.Prefs
import com.ivan.grumpweather.data.WeatherApi
import com.ivan.grumpweather.grumpy.Grumpy
import com.ivan.grumpweather.grumpy.Mood
import com.ivan.grumpweather.widget.GrumpWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface UiState {
    data object NoPlace : UiState
    data object Loading : UiState
    data class Error(val message: String, val details: String? = null) : UiState
    data class Ready(val place: Place, val forecast: Forecast, val mood: Mood) : UiState
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private var job: Job? = null

    var state by mutableStateOf<UiState>(UiState.Loading)
        private set

    /** Меняется по тапу на фразу — чтобы обосрать погоду по-другому. */
    var seed by mutableIntStateOf(Random.nextInt())
        private set

    private var place: Place? = prefs.place

    init {
        val p = place
        when {
            p == null -> state = UiState.NoPlace
            p.fromGps && LocationHelper.hasPermission(app) -> locate()
            else -> load(p)
        }
    }

    fun reroll() {
        seed = Random.nextInt()
    }

    fun refresh() {
        val p = place
        when {
            p == null -> state = UiState.NoPlace
            p.fromGps && LocationHelper.hasPermission(getApplication()) -> locate()
            else -> load(p)
        }
    }

    fun pick(p: Place) = load(p)

    fun permissionDenied() {
        val p = place
        if (p != null && !p.fromGps) {
            load(p)
        } else {
            state = UiState.Error("Не хочешь говорить, где ты — твоё дело. Введи город руками.")
        }
    }

    fun locate() {
        job?.cancel()
        state = UiState.Loading
        job = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val loc = try {
                LocationHelper.getLocation(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (loc == null) {
                state = UiState.Error(
                    "Геолокация нихуя не ловит. Включи её в настройках или введи город руками."
                )
                return@launch
            }
            val name = LocationHelper.cityName(ctx, loc.latitude, loc.longitude) ?: "Где-то тут"
            fetch(Place(name, loc.latitude, loc.longitude, region = null, fromGps = true))
        }
    }

    private fun load(p: Place) {
        job?.cancel()
        state = UiState.Loading
        job = viewModelScope.launch { fetch(p) }
    }

    private suspend fun fetch(p: Place) {
        place = p
        prefs.place = p
        try {
            val f = WeatherApi.forecast(p.lat, p.lon)
            val c = f.current
            val mood = Grumpy.classify(
                code = c.code, temp = c.temp, feels = c.feelsLike, humidity = c.humidity,
                wind = c.windMs, gust = c.gustMs, precip = c.precipitation,
            )
            seed = Random.nextInt()
            state = UiState.Ready(p, f, mood)
            runCatching { GrumpWidget.push(getApplication<Application>(), p, f) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = UiState.Error(
                "Не смог достучаться до погоды. Интернет сдох, или сервер, или всё сразу.",
                e.message,
            )
        }
    }
}
