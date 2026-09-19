package com.leo.eats.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.leo.eats.ui.components.PhotoStrip
import com.leo.eats.ui.components.RatingStars
import com.leo.eats.ui.components.formatVisitTime
import com.leo.eats.ui.components.rememberPhotoPicker
import com.leo.eats.ui.theme.menuColors
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * W6 记一笔（US-03）：时间默认现在（可改，日期→时间两步），
 * 评分/花费/感想/照片全可选，落账即创建 Visit。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogVisitSheet(
    vm: com.leo.eats.ui.AppViewModel,
    placeName: String,
    onLog: (at: Long, rating: Int?, cost: Double?, text: String, photoUris: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var at by remember { mutableStateOf(System.currentTimeMillis()) }
    var rating by remember { mutableStateOf<Int?>(null) }
    var cost by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val uris = remember { mutableStateListOf<String>() }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val photoPicker = rememberPhotoPicker { uri -> if (uri != null) uris += uri.toString() }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("记一笔 · $placeName", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = menuColors().inkFaint,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatVisitTime(at),
                    style = MaterialTheme.typography.titleSmall,
                    color = menuColors().ink,
                )
                TextButton(onClick = { showDate = true }) { Text("改") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "评分",
                    style = MaterialTheme.typography.labelLarge,
                    color = menuColors().inkFaint,
                    modifier = Modifier.weight(1f),
                )
                RatingStars(rating = rating, onChange = { rating = it }, size = 26.dp)
            }

            OutlinedTextField(
                value = cost,
                onValueChange = { cost = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("花费 ¥（可选）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("一句话感想（可选）") },
                minLines = 2,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                "照片（可选）",
                style = MaterialTheme.typography.labelLarge,
                color = menuColors().inkFaint,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(6.dp))
            PhotoStrip(
                models = uris.toList(),
                onRemove = { i -> uris.removeAt(i) },
                onAdd = { photoPicker() },
                modifier = Modifier.align(Alignment.Start),
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onLog(at, rating, cost.trim().toDoubleOrNull(), text, uris.toList()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("落账") }
        }
    }

    if (showDate) {
        val zone = ZoneId.systemDefault()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = at,
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val time = Instant.ofEpochMilli(at).atZone(zone).toLocalTime()
                        at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                    showTime = true
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(at).atZone(zone)
        val timeState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("几点吃的") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                    at = date.atTime(LocalTime.of(timeState.hour, timeState.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
        )
    }
}

