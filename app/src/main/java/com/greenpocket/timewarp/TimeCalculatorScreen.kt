//
//  TimeCalculatorScreen.kt
//  Time Warp (Android)
//
//  Change (November 9, 2026): new file. Android copy of TimeCalculatorView
//  from the iOS app's Shared/TimeCalculator.swift: the menu, Time
//  Difference, Corrected Time (with the daylight saving card and the
//  repeated-hour choice) and Retention Date. Same maths, same wording.
//

@file:OptIn(ExperimentalMaterial3Api::class)

package com.greenpocket.timewarp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class CalcMode { MENU, DIFFERENCE, CORRECTED, RETENTION }

/**
 * Everything the calculator remembers. Kept outside the screen so values
 * survive switching to the Clock or Settings tab and back, like on iOS.
 */
class CalcState {
    var mode by mutableStateOf(CalcMode.MENU)

    var dvrTime by mutableStateOf(Instant.now())
    var actualTime by mutableStateOf(Instant.now())
    var timeDifference by mutableStateOf("")
    var timeDifferenceColor by mutableStateOf(Ios.green)
    var signedOffsetSeconds by mutableIntStateOf(0)

    var correctedTime by mutableStateOf(Instant.now())
    var correctedEndTime by mutableStateOf(Instant.now())
    var isRangeMode by mutableStateOf(false)
    var downloadInstruction by mutableStateOf("")

    var offsetDaysStr by mutableStateOf("0")
    var offsetHoursStr by mutableStateOf("00")
    var offsetMinutesStr by mutableStateOf("00")
    var offsetSecondsStr by mutableStateOf("00")
    var isOffsetFast by mutableStateOf(true)

    var retentionDate by mutableStateOf(LocalDate.now())

    /** "Did the DVR change its clock for DST?" null = not answered. Saved. */
    var dvrFollowsDST by mutableStateOf(DaylightSavingStore.dvrFollowsDST)
        private set

    fun answerDvrFollowsDST(value: Boolean?) {
        dvrFollowsDST = value
        DaylightSavingStore.dvrFollowsDST = value
    }

    /** First or second time through the repeated hour on fall-back day. */
    var startIsSecondPass by mutableStateOf(false)
    var endIsSecondPass by mutableStateOf(false)

    val manualOffsetSeconds: Int
        get() {
            val d = offsetDaysStr.toIntOrNull() ?: 0
            val h = offsetHoursStr.toIntOrNull() ?: 0
            val m = offsetMinutesStr.toIntOrNull() ?: 0
            val s = offsetSecondsStr.toIntOrNull() ?: 0
            val magnitude = d * 86400 + h * 3600 + m * 60 + s
            return if (isOffsetFast) -magnitude else magnitude
        }

    // MARK: Daylight saving (Corrected Time)

    private fun effective(i: Instant, secondPass: Boolean): Instant {
        val pair = DaylightSaving.repeatedHour(i) ?: return i
        return if (secondPass) pair.second else pair.first
    }

    val effectiveStart: Instant get() = effective(correctedTime, startIsSecondPass)
    val effectiveEnd: Instant get() = effective(correctedEndTime, endIsSecondPass)

    /** The clock change between the footage needed and today, if any. */
    fun dstChange(): ClockChange? {
        val now = Instant.now()
        DaylightSaving.relevantChange(effectiveStart, now)?.let { return it }
        if (isRangeMode) return DaylightSaving.relevantChange(effectiveEnd, now)
        return null
    }

    /** Seconds to add to today's offset for footage at `i`, when the DVR did NOT change its clock. */
    private fun dstShift(i: Instant): Int {
        val now = Instant.now()
        if (dvrFollowsDST != false || DaylightSaving.relevantChange(i, now) == null) return 0
        return DaylightSaving.offsetShift(i, now)
    }

    // MARK: Calculations

    fun calculateTimeDifference() {
        val difference = actualTime.truncatedTo(ChronoUnit.SECONDS).epochSecond -
            dvrTime.truncatedTo(ChronoUnit.SECONDS).epochSecond
        signedOffsetSeconds = difference.toInt()
        val total = abs(signedOffsetSeconds)

        val days = total / 86400
        val hours = (total % 86400) / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days day${if (days != 1) "s" else ""}")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (secs > 0) parts.add("${secs}s")
        val formatted = if (parts.isEmpty()) "0s" else parts.joinToString(" ")
        val direction = when {
            signedOffsetSeconds > 0 -> "slower"
            signedOffsetSeconds < 0 -> "faster"
            else -> "synchronized"
        }

        timeDifference = if (signedOffsetSeconds == 0) "DVR is Accurate" else "DVR is $formatted $direction"
        timeDifferenceColor = when {
            signedOffsetSeconds > 0 -> Ios.red
            signedOffsetSeconds < 0 -> Ios.green
            else -> Ios.gray
        }
        populateOffsetFields(signedOffsetSeconds)

        // About 1 hour off (within 5 minutes) usually means the DVR didn't
        // change its clock for daylight saving, so the DST question starts on No.
        if (abs(abs(signedOffsetSeconds) - 3600) <= 300) answerDvrFollowsDST(false)
    }

    private fun populateOffsetFields(signed: Int) {
        isOffsetFast = signed < 0
        val total = abs(signed)
        val remainder = total % 86400
        offsetDaysStr = (total / 86400).toString()
        offsetHoursStr = two(remainder / 3600)
        offsetMinutesStr = two((remainder % 3600) / 60)
        offsetSecondsStr = two(remainder % 60)
    }

    fun calculateDownloadTime() {
        val offset = manualOffsetSeconds
        val start = effectiveStart
        val end = effectiveEnd

        if (dstChange() != null && dvrFollowsDST == null) {
            downloadInstruction = "Answer the daylight saving question above first"
            return
        }

        val startShift = dstShift(start)
        val endShift = if (isRangeMode) dstShift(end) else 0

        // DVR reading = real reading minus the offset that applied then.
        val pattern = "MMMM d, yyyy 'at' h:mm:ss a"
        val startString = DaylightSaving.clockString(start, -(offset + startShift), pattern)
        val endString = DaylightSaving.clockString(end, -(offset + endShift), pattern)

        downloadInstruction = if (isRangeMode) {
            "Download from\n$startString\nto\n$endString"
        } else {
            "Download\n$startString"
        }

        val shift = if (startShift != 0) startShift else endShift
        if (shift != 0) {
            val hours = abs(shift) / 3600
            downloadInstruction += "\n\n(Includes $hours hour${if (hours == 1) "" else "s"} DST adjustment)"
        }
    }

    fun resetAll() {
        dvrTime = Instant.now()
        actualTime = Instant.now()
        timeDifference = ""
        signedOffsetSeconds = 0

        correctedTime = Instant.now()
        correctedEndTime = Instant.now()
        isRangeMode = false
        downloadInstruction = ""
        answerDvrFollowsDST(null)
        startIsSecondPass = false
        endIsSecondPass = false

        offsetDaysStr = "0"
        offsetHoursStr = "00"
        offsetMinutesStr = "00"
        offsetSecondsStr = "00"
        isOffsetFast = true

        retentionDate = LocalDate.now()
    }
}

const val ACTUAL_TIME_INFO = """Sync Actual Time uses this device's own clock. The app doesn't contact a time server itself.

With automatic date and time turned on (Settings → System → Date & time), Android keeps the clock accurate using network time, along with the mobile network and GPS. It's usually right to well under a second.

If automatic time is off, or the time zone is set by hand, this time may be wrong."""

@Composable
fun TimeCalculatorScreen(s: CalcState) {
    var showResetConfirm by remember { mutableStateOf(false) }

    // Re-check the clock-change cards whenever the app comes back to the
    // front (the date, time zone or Settings switch may have changed).
    var dstRefresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        dstRefresh++
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        dstRefresh.let { } // read so the screen refreshes when it changes
        when (s.mode) {
            CalcMode.MENU -> MenuScreen(s) { showResetConfirm = true }
            CalcMode.DIFFERENCE -> DifferenceScreen(s) { showResetConfirm = true }
            CalcMode.CORRECTED -> CorrectedScreen(s) { showResetConfirm = true }
            CalcMode.RETENTION -> RetentionScreen(s) { showResetConfirm = true }
        }
    }

    if (showResetConfirm) {
        ModalBottomSheet(onDismissRequest = { showResetConfirm = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Reset Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "This cannot be undone. All times, offsets, and results will be cleared.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { s.resetAll(); showResetConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Ios.red),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset All Calculator Fields")
                }
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        }
    }
}

// MARK: - Menu

@Composable
private fun MenuScreen(s: CalcState, onReset: () -> Unit) {
    MenuCard(Icons.Filled.History, "Time Difference", "Compare DVR time to real time", Ios.blue) {
        s.mode = CalcMode.DIFFERENCE
    }
    MenuCard(Icons.Filled.Sync, "Corrected Time", "Find the real download time", Ios.orange) {
        s.mode = CalcMode.CORRECTED
    }
    MenuCard(Icons.Filled.Event, "Retention Date", "Track storage expiration", Ios.pink) {
        s.mode = CalcMode.RETENTION
    }
    TextButton(onClick = onReset) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = Ios.red)
        Spacer(Modifier.width(6.dp))
        Text("Reset All Values", color = Ios.red)
    }
}

@Composable
private fun MenuCard(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(2.dp, color.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, tint = Ios.blue, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - Time Difference

@Composable
private fun DifferenceScreen(s: CalcState, onReset: () -> Unit) {
    TimeAndDateFields("DVR TIME", s.dvrTime, { s.dvrTime = it })
    TimeAndDateFields(
        "ACTUAL TIME", s.actualTime, { s.actualTime = it },
        actionTitle = "Sync Actual Time",
        infoTitle = "Where This Time Comes From",
        infoText = ACTUAL_TIME_INFO,
    ) { s.actualTime = Instant.now() }

    Button(
        onClick = { s.calculateTimeDifference() },
        colors = ButtonDefaults.buttonColors(containerColor = Ios.blue),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { Text("Calculate", style = MaterialTheme.typography.titleMedium) }

    if (s.timeDifference.isNotEmpty()) {
        Text("Time Difference:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ResultBox(s.timeDifference, s.timeDifferenceColor)
        RecentClockChangeNotice(s)
    }

    TextButton(onClick = onReset) { Text("Reset Values", color = Ios.red) }
}

/** Reminder under the result for 7 days after a clock change. */
@Composable
private fun RecentClockChangeNotice(s: CalcState) {
    val change = DaylightSaving.recentChange(7) ?: return
    val days = DaylightSaving.daysSince(change)
    val whenText = if (days == 0) "today" else "$days day${if (days == 1) "" else "s"} ago"
    val aboutOneHour = abs(abs(s.signedOffsetSeconds) - 3600) <= 300
    YellowBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Update, contentDescription = null, tint = Ios.orange)
            Spacer(Modifier.width(6.dp))
            Text(
                "Clock change $whenText: confirm DVR settings",
                style = MaterialTheme.typography.titleSmall,
                color = Ios.orange,
            )
        }
        Text(
            if (aboutOneHour) {
                "${change.headline} ${change.shortDayString}. This looks like the DVR didn't change its clock. Footage from before ${change.shortDayString} may need a different offset; Corrected Time will handle it."
            } else {
                "${change.headline} ${change.shortDayString}. If this DVR doesn't change its clock for DST, footage from before ${change.shortDayString} will be 1 hour off; Corrected Time will handle it."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// MARK: - Corrected Time

@Composable
private fun CorrectedScreen(s: CalcState, onReset: () -> Unit) {
    CalcCard(borderColor = if (s.isOffsetFast) Ios.green else Ios.red) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CORRECTED TIME", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Time Difference", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        OffsetBox("DAYS", s.offsetDaysStr) { s.offsetDaysStr = it }
                        OffsetBox("HRS", s.offsetHoursStr) { s.offsetHoursStr = it }
                        OffsetBox("MIN", s.offsetMinutesStr) { s.offsetMinutesStr = it }
                        OffsetBox("SEC", s.offsetSecondsStr) { s.offsetSecondsStr = it }
                    }
                    Segmented(listOf("Fast", "Slow"), if (s.isOffsetFast) 0 else 1) { s.isOffsetFast = it == 0 }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable Range Mode", Modifier.weight(1f))
                Switch(
                    checked = s.isRangeMode,
                    onCheckedChange = { s.isRangeMode = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ios.orange),
                )
            }

            TimeAndDateFields(
                if (s.isRangeMode) "Real Start Time Needed" else "Real-World Time Needed",
                s.correctedTime, { s.correctedTime = it },
            )
            RepeatedHourPicker(s.correctedTime, s.startIsSecondPass) { s.startIsSecondPass = it }

            if (s.isRangeMode) {
                TimeAndDateFields("End Time", s.correctedEndTime, { s.correctedEndTime = it })
                RepeatedHourPicker(s.correctedEndTime, s.endIsSecondPass) { s.endIsSecondPass = it }
            }

            DstCard(s)
        }
    }

    TextButton(onClick = onReset) { Text("Reset Values", color = Ios.red) }

    Button(
        onClick = { s.calculateDownloadTime() },
        colors = ButtonDefaults.buttonColors(containerColor = Ios.orange),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
    ) { Text("Calculate Download Time") }

    if (s.downloadInstruction.isNotEmpty()) {
        ResultBox(s.downloadInstruction, Ios.orange)
    }
}

/** Yellow card when the footage needed is across a clock change from today. */
@Composable
private fun DstCard(s: CalcState) {
    val change = s.dstChange() ?: return
    YellowBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Ios.orange)
            Spacer(Modifier.width(6.dp))
            Text("Clock change in between", style = MaterialTheme.typography.titleMedium, color = Ios.orange)
        }
        Text("${change.headline} ${change.fullString}, between this footage and today.", style = MaterialTheme.typography.bodyMedium)
        Text("Did the DVR change its clock for DST?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Segmented(
            listOf("Yes", "No"),
            when (s.dvrFollowsDST) { true -> 0; false -> 1; null -> -1 },
        ) { s.answerDvrFollowsDST(it == 0) }
        Text(
            when (s.dvrFollowsDST) {
                true -> "No adjustment: the offset is the same as today."
                false -> "The result will be adjusted for the clock change."
                null -> "Answer this before calculating."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Not sure? Check the DVR's date/time settings, or scrub the footage at ${change.timeString} on ${change.shortDayString}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** On fall-back day, a time in the repeated hour gets a First / Second choice. */
@Composable
private fun RepeatedHourPicker(time: Instant, isSecondPass: Boolean, onChange: (Boolean) -> Unit) {
    if (DaylightSaving.repeatedHour(time) == null) return
    val day = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)
        .format(time.atZone(DaylightSaving.zone))
    YellowBox {
        Text("The clocks went back on $day, so this time happened twice. Which one?", style = MaterialTheme.typography.labelMedium)
        Segmented(listOf("First time", "Second time"), if (isSecondPass) 1 else 0) { onChange(it == 1) }
    }
}

// MARK: - Retention Date

@Composable
private fun RetentionScreen(s: CalcState, onReset: () -> Unit) {
    CalcCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("RETENTION DATE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            val state = rememberDatePickerState(
                initialSelectedDateMillis = s.retentionDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            // Picking a day updates the result straight away.
            LaunchedEffect(state.selectedDateMillis) {
                state.selectedDateMillis?.let {
                    s.retentionDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                }
            }
            // Reset sets the date back to today; move the calendar with it.
            LaunchedEffect(s.retentionDate) {
                val ms = s.retentionDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                if (state.selectedDateMillis != ms) {
                    state.selectedDateMillis = ms
                    state.displayedMonthMillis = ms
                }
            }
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
            )

            ResultBox(
                "${RetentionDate.dateString(s.retentionDate)}\n${RetentionDate.durationString(s.retentionDate)}",
                Ios.pink,
            )
        }
    }
    TextButton(onClick = onReset) { Text("Reset Values", color = Ios.red) }
}

// MARK: - Small shared pieces

@Composable
private fun ResultBox(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            Modifier.padding(16.dp),
            color = color,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun YellowBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Ios.yellow.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Ios.yellow.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** iOS-style segmented control. selected = -1 means nothing chosen yet. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                icon = {},
            ) { Text(label) }
        }
    }
}

/** One of the DAYS / HRS / MIN / SEC boxes. Empties when tapped, ready to type. */
@Composable
private fun OffsetBox(label: String, text: String, onChange: (String) -> Unit) {
    var wasFocused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { onChange(it.filter(Char::isDigit)) },
            placeholder = { Text("0", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier
                .width(68.dp)
                .onFocusChanged {
                    if (it.isFocused != wasFocused) {
                        wasFocused = it.isFocused
                        if (it.isFocused) onChange("")
                    }
                },
        )
    }
}
