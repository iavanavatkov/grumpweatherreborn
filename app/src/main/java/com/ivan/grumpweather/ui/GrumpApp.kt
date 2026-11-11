package com.ivan.grumpweather.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivan.grumpweather.UiState
import com.ivan.grumpweather.WeatherViewModel
import com.ivan.grumpweather.data.DayWeather
import com.ivan.grumpweather.data.HourWeather
import com.ivan.grumpweather.data.LocationHelper
import com.ivan.grumpweather.data.Place
import com.ivan.grumpweather.grumpy.Grumpy
import com.ivan.grumpweather.grumpy.Mood
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

private val Ink = Color.White
private val Dim = Color.White.copy(alpha = 0.72f)
private val Faint = Color.White.copy(alpha = 0.45f)
private val CardBg = Color.White.copy(alpha = 0.12f)

@Composable
fun GrumpApp(vm: WeatherViewModel = viewModel()) {
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) vm.locate() else vm.permissionDenied()
    }
    val askLocation: () -> Unit = {
        if (LocationHelper.hasPermission(context)) {
            vm.locate()
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }
    val openSearch: () -> Unit = { showSearch = true }

    val state = vm.state
    val mood = (state as? UiState.Ready)?.mood
    val isNight = (state as? UiState.Ready)?.forecast?.current?.isDay == false

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(moodGradient(mood, isNight)))
    ) {
        Crossfade(targetState = state, label = "state") { s ->
            when (s) {
                UiState.NoPlace -> WelcomeView(onLocate = askLocation, onSearch = openSearch)
                UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(
                    s, onRetry = vm::refresh, onSearch = openSearch, onLocate = askLocation
                )
                is UiState.Ready -> ReadyView(
                    s = s,
                    seed = vm.seed,
                    onReroll = vm::reroll,
                    onRefresh = vm::refresh,
                    onSearch = openSearch,
                    onLocate = askLocation,
                )
            }
        }
    }

    if (showSearch) {
        CitySearchDialog(
            onDismiss = { showSearch = false },
            onPick = { p: Place ->
                showSearch = false
                vm.pick(p)
            },
        )
    }
}

// ───────────────────────── экраны ─────────────────────────

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun WelcomeView(onLocate: () -> Unit, onSearch: () -> Unit) {
    CenteredColumn {
        Text("🌩️", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Где ты, блядь?",
            fontSize = 36.sp, fontWeight = FontWeight.Black, color = Ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Мне надо знать, где обсирать погоду.",
            fontSize = 17.sp, color = Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton("📍 Найди меня сам", onLocate)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Введу город руками", onSearch)
    }
}

@Composable
private fun LoadingView() {
    val phrase = remember { Grumpy.loading() }
    CenteredColumn {
        CircularProgressIndicator(color = Ink)
        Spacer(Modifier.height(20.dp))
        Text(phrase, color = Dim, fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorView(s: UiState.Error, onRetry: () -> Unit, onSearch: () -> Unit, onLocate: () -> Unit) {
    CenteredColumn {
        Text("🤬", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(s.message, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        s.details?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Faint, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Ещё раз, блядь", onRetry)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Ввести город", onSearch)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("📍 По геолокации", onLocate)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyView(
    s: UiState.Ready,
    seed: Int,
    onReroll: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onLocate: () -> Unit,
) {
    val c = s.forecast.current
    val headline = remember(seed, s.mood, c.isDay) { Grumpy.headline(s.mood, c.isDay, Random(seed)) }
    val sub = remember(seed, s.mood) { Grumpy.sub(s.mood, Random(seed + 7)) }
    val extras = remember(s) {
        Grumpy.extras(c.temp, c.feelsLike, c.humidity, c.windMs, c.gustMs, c.isDay, s.mood)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        TopBar(s.place, onSearch, onLocate, onRefresh)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Grumpy.emoji(c.code, c.isDay), fontSize = 60.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(fmtTemp(c.temp), fontSize = 68.sp, fontWeight = FontWeight.Light, color = Ink, lineHeight = 70.sp)
                Text(
                    "${Grumpy.codeName(c.code)} · ощущается ${fmtTemp(c.feelsLike)}",
                    color = Dim, fontSize = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            headline,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Black,
            color = Ink,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onReroll() },
        )
        Text("тапни — обосру по-другому", color = Faint, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Text(sub, fontSize = 17.sp, lineHeight = 23.sp, color = Dim)

        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                extras.forEach { Chip(it) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Сырость", "${c.humidity}%", Modifier.weight(1f))
            StatCard("Дует", "${fmt1(c.windMs)} м/с", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Порывы, сука", "${fmt1(c.gustMs)} м/с", Modifier.weight(1f))
            StatCard("Осадки", "${fmt1(c.precipitation)} мм", Modifier.weight(1f))
        }

        SectionTitle("Ближайшие сутки")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(s.forecast.hours) { h -> HourCard(h) }
        }

        SectionTitle("Неделя пиздеца")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            s.forecast.days.forEachIndexed { i, d -> DayRow(d, i) }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Данные на ${c.time.takeLast(5)} · погода от Open-Meteo.com",
            color = Faint, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ───────────────────────── кусочки ─────────────────────────

@Composable
private fun TopBar(place: Place, onSearch: () -> Unit, onLocate: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable { onSearch() }
        ) {
            Text(
                place.name,
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (place.fromGps) "📍 по геолокации" else (place.region ?: "")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onLocate) { Icon(Icons.Filled.LocationOn, "Геолокация", tint = Ink) }
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Поиск города", tint = Ink) }
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Обновить", tint = Ink) }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        color = Ink,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(14.dp)
    ) {
        Text(label, color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(26.dp))
    Text(text, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HourCard(h: HourWeather) {
    val hour = h.time.substring(11, 13).toIntOrNull() ?: 12
    val isDay = hour in 6..18
    val label = remember(h.time) {
        val mood = Grumpy.classify(h.code, h.temp, h.feelsLike, h.humidity, h.windMs, h.windMs)
        Grumpy.short(mood, Random(h.time.hashCode()))
    }
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(h.time.substring(11, 16), color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(Grumpy.emoji(h.code, isDay), fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(fmtTemp(h.temp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            label, color = Dim, fontSize = 11.sp, lineHeight = 13.sp,
            textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
        )
        Text(
            if (h.precipProb >= 20) "💧${h.precipProb}%" else " ",
            color = Faint, fontSize = 11.sp,
        )
    }
}

@Composable
private fun DayRow(d: DayWeather, index: Int) {
    val label = remember(d.date) {
        val mood = Grumpy.classify(d.code, d.tMax, d.tMax, d.humidity, d.windMax, d.windMax)
        Grumpy.short(mood, Random(d.date.hashCode()))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dayName(d.date, index), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (d.precipProb >= 20) "💧${d.precipProb}%" else "",
            color = Faint, fontSize = 12.sp,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Text(Grumpy.emoji(d.code, true), fontSize = 24.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${fmtTemp(d.tMax)} / ${fmtTemp(d.tMin)}",
            color = Ink, fontSize = 15.sp,
            modifier = Modifier.width(92.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color(0xFF1B1C2A)),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp)) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp)) }
}

// ───────────────────────── утилиты ─────────────────────────

private val WEEKDAYS = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun dayName(date: String, index: Int): String {
    return try {
        val d = LocalDate.parse(date)
        val dm = "%02d.%02d".format(d.dayOfMonth, d.monthValue)
        when (index) {
            0 -> "Сегодня"
            1 -> "Завтра"
            else -> "${WEEKDAYS[d.dayOfWeek.value - 1]}, $dm"
        }
    } catch (e: Exception) {
        date
    }
}

internal fun fmtTemp(t: Double): String {
    val r = t.roundToInt()
    return when {
        r > 0 -> "+$r°"
        r < 0 -> "−${-r}°"
        else -> "0°"
    }
}

private fun fmt1(v: Double): String = String.format(Locale.forLanguageTag("ru"), "%.1f", v)

private fun moodGradient(mood: Mood?, night: Boolean): List<Color> {
    val base = when (mood) {
        null -> listOf(Color(0xFF2B2D42), Color(0xFF14151F))
        Mood.THUNDER -> listOf(Color(0xFF3A2559), Color(0xFF0F0A1A))
        Mood.BLIZZARD, Mood.SNOW -> listOf(Color(0xFF5C7A99), Color(0xFF26364A))
        Mood.ICE -> listOf(Color(0xFF4F7C8A), Color(0xFF1D3540))
        Mood.DOWNPOUR, Mood.RAIN -> listOf(Color(0xFF34495E), Color(0xFF151F2A))
        Mood.DRIZZLE, Mood.FOG, Mood.DREARY -> listOf(Color(0xFF636B78), Color(0xFF2A2E35))
        Mood.STORM_WIND -> listOf(Color(0xFF3D5A6C), Color(0xFF16232B))
        Mood.EXTREME_COLD, Mood.COLD -> listOf(Color(0xFF1E5AA8), Color(0xFF0B1E3D))
        Mood.FROSTY, Mood.CHILLY -> listOf(Color(0xFF4A6B82), Color(0xFF1E2D38))
        Mood.HAMSIN -> listOf(Color(0xFFC08A3E), Color(0xFF5C3A12))
        Mood.INFERNO -> listOf(Color(0xFFD9381E), Color(0xFF5A0F05))
        Mood.SAUNA -> listOf(Color(0xFFB5543A), Color(0xFF4A2A1E))
        Mood.HOT -> listOf(Color(0xFFE07A22), Color(0xFF7A2E0B))
        Mood.SWAMP -> listOf(Color(0xFF4E6B4A), Color(0xFF1C2B1A))
        Mood.MILD -> listOf(Color(0xFF4F7A6B), Color(0xFF1F3330))
        Mood.NICE -> listOf(Color(0xFF2F8FD8), Color(0xFF1A4D7A))
    }
    return if (night) base.map { it.darken(0.55f) } else base
}

private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)
