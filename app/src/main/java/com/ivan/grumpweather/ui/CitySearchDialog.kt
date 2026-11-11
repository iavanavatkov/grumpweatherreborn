package com.ivan.grumpweather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivan.grumpweather.data.Place
import com.ivan.grumpweather.data.WeatherApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun CitySearchDialog(onDismiss: () -> Unit, onPick: (Place) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            status = null
            return@LaunchedEffect
        }
        delay(400)
        status = "Ищу, бля…"
        try {
            val found = WeatherApi.searchCity(q)
            results = found
            status = if (found.isEmpty()) "Нихуя не нашёл. Ты точно правильно написал?" else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
            status = "Поиск сдох: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Да похуй") }
        },
        title = { Text("Какой ещё город?") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Тель-Авив, Мухосранск…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            p.region?.let {
                                Text(
                                    it,
                                    fontSize = 12.sp,
                                    color = LocalContentColor.current.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
