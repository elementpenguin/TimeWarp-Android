//
//  TimeFields.kt
//  Time Warp (Android)
//
//  Change (November 9, 2026): new file. Android copy of TimeAndDateFields
//  from the iOS app's Shared/Clock.swift: a card with a title, an optional
//  (i), the date (tap to pick another) and HOURS / MINUTES / SECONDS boxes.
//  A box empties when tapped, the time only changes once you type, two
//  digits jump to the next box, and a box left empty gets its value back.
//

@file:OptIn(ExperimentalMaterial3Api::class)

package com.greenpocket.timewarp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "07" for 7. */
fun two(n: Int): String = String.format(Locale.US, "%02d", n)

/** The light-grey (light mode) / dark-grey (dark mode) card behind each section. */
@Composable
fun CalcCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(2.dp, it) },
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

private enum class TimeField { HOURS, MINUTES, SECONDS }

@Composable
fun TimeAndDateFields(
    title: String,
    value: Instant,
    onValueChange: (Instant) -> Unit,
    actionTitle: String? = null,
    infoTitle: String? = null,
    infoText: String? = null,
    action: (() -> Unit)? = null,
) {
    val zone = DaylightSaving.zone
    val current by rememberUpdatedState(value)
    val local = value.atZone(zone)

    var showInfo by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var hStr by remember { mutableStateOf(two(local.hour)) }
    var mStr by remember { mutableStateOf(two(local.minute)) }
    var sStr by remember { mutableStateOf(two(local.second)) }
    var focused by remember { mutableStateOf<TimeField?>(null) }

    val hReq = remember { FocusRequester() }
    val mReq = remember { FocusRequester() }
    val sReq = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun refreshStrings(i: Instant) {
        val l = i.atZone(zone)
        hStr = two(l.hour); mStr = two(l.minute); sStr = two(l.second)
    }

    // The date changed from outside (Sync, Reset, calendar): show it, unless
    // someone is typing in one of the boxes.
    LaunchedEffect(value) {
        if (focused == null) refreshStrings(value)
    }

    fun updateFromStrings() {
        val h = (hStr.toIntOrNull() ?: 0).coerceIn(0, 23)
        val m = (mStr.toIntOrNull() ?: 0).coerceIn(0, 59)
        val s = (sStr.toIntOrNull() ?: 0).coerceIn(0, 59)
        val day = current.atZone(zone).toLocalDate()
        onValueChange(ZonedDateTime.of(day, LocalTime.of(h, m, s), zone).toInstant())
    }

    fun handleInput(raw: String, field: TimeField, next: FocusRequester?) {
        val filtered = raw.filter { it.isDigit() }.takeLast(2)
        when (field) {
            TimeField.HOURS -> hStr = filtered
            TimeField.MINUTES -> mStr = filtered
            TimeField.SECONDS -> sStr = filtered
        }
        // A tapped box is empty, ready to type. Nothing changes until a digit is typed.
        if (filtered.isEmpty()) return
        updateFromStrings()
        if (filtered.length == 2) {
            if (next != null) next.requestFocus() else focusManager.clearFocus()
        }
    }

    fun onFocus(field: TimeField, isFocused: Boolean) {
        if (isFocused) {
            focused = field
            when (field) {
                TimeField.HOURS -> hStr = ""
                TimeField.MINUTES -> mStr = ""
                TimeField.SECONDS -> sStr = ""
            }
        } else if (focused == field) {
            focused = null
        }
        // A box left empty shows its value again once you leave it.
        val l = current.atZone(zone)
        if (focused != TimeField.HOURS && hStr.isEmpty()) hStr = two(l.hour)
        if (focused != TimeField.MINUTES && mStr.isEmpty()) mStr = two(l.minute)
        if (focused != TimeField.SECONDS && sStr.isEmpty()) sStr = two(l.second)
    }

    CalcCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (infoText != null) {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = infoTitle ?: "More information", tint = Ios.blue)
                    }
                }
            }

            // The date. Tap it to pick another day.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showCalendar = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Pick a date",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                NumberBox("HOURS", hStr, hReq, { onFocus(TimeField.HOURS, it) }) {
                    handleInput(it, TimeField.HOURS, mReq)
                }
                NumberBox("MINUTES", mStr, mReq, { onFocus(TimeField.MINUTES, it) }) {
                    handleInput(it, TimeField.MINUTES, sReq)
                }
                NumberBox("SECONDS", sStr, sReq, { onFocus(TimeField.SECONDS, it) }) {
                    handleInput(it, TimeField.SECONDS, null)
                }
            }

            if (actionTitle != null && action != null) {
                OutlinedButton(
                    onClick = action,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(actionTitle, color = Ios.blue) }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text(infoTitle ?: "About") },
            text = { Text(infoText ?: "") },
        )
    }

    if (showCalendar) {
        // The date picker works in UTC days; convert to and from the phone's day.
        val startMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = current.atZone(zone).toLocalTime()
                        onValueChange(ZonedDateTime.of(day, time, zone).toInstant())
                    }
                    showCalendar = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

@Composable
private fun NumberBox(
    label: String,
    text: String,
    requester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
) {
    var wasFocused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("00", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = MaterialTheme.typography.titleMedium.fontSize),
            modifier = Modifier
                .width(76.dp)
                .focusRequester(requester)
                .onFocusChanged {
                    // Only react to real changes (Compose also reports the starting state).
                    if (it.isFocused != wasFocused) {
                        wasFocused = it.isFocused
                        onFocusChange(it.isFocused)
                    }
                },
        )
    }
}
