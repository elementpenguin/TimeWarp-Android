//
//  DaylightSaving.kt
//  Time Warp (Android)
//
//  Change (November 9, 2026): new file. Android copy of the iOS app's
//  Shared/DaylightSaving.swift and the parts of CalculatorShared.swift that
//  Time Warp uses. Same rules, same wording. If you change the DST logic in
//  the iOS app, change it here too so both apps give the same answers.
//
//  Where the DST dates come from: Android's built-in time zone data for the
//  phone's current region (java.time). It's on the device, so it works
//  offline.
//

package com.greenpocket.timewarp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The phone's saved settings (Android's version of UserDefaults). */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("timewarp", Context.MODE_PRIVATE)
    }
}

/** Settings → "Daylight Saving Help". On by default. */
object DaylightSavingStore {
    const val KEY = "julias_dstHelpEnabled"
    private const val ANSWER_KEY = "julias_dvrFollowsDST"

    var isEnabled: Boolean
        get() = Prefs.sp.getBoolean(KEY, true)
        set(value) = Prefs.sp.edit { putBoolean(KEY, value) }

    /** The answer to "Did the DVR change its clock for DST?". Null = not answered. */
    var dvrFollowsDST: Boolean?
        get() = if (Prefs.sp.contains(ANSWER_KEY)) Prefs.sp.getBoolean(ANSWER_KEY, false) else null
        set(value) = Prefs.sp.edit {
            if (value == null) remove(ANSWER_KEY) else putBoolean(ANSWER_KEY, value)
        }
}

/** One clock change, e.g. "daylight saving ended Nov 1 at 2:00 AM". */
data class ClockChange(
    /** The exact moment the clocks changed. */
    val instant: Instant,
    /** -3600 when the clocks went back an hour (fall), +3600 when they went forward (spring). */
    val shiftSeconds: Int,
) {
    val isFallBack: Boolean get() = shiftSeconds < 0
    val headline: String get() = if (isFallBack) "Daylight saving ended" else "Daylight saving started"
    val shortDayString: String get() = DaylightSaving.formatBeforeChange(instant, "MMM d")
    val dayYearString: String get() = DaylightSaving.formatBeforeChange(instant, "MMM d, yyyy")
    val timeString: String get() = DaylightSaving.formatBeforeChange(instant, "h:mm a")
    val fullString: String get() = DaylightSaving.formatBeforeChange(instant, "MMM d, yyyy 'at' h:mm a")
}

object DaylightSaving {
    /** The phone's current region, read fresh each time. */
    val zone: ZoneId get() = ZoneId.systemDefault()

    private fun offset(i: Instant): Int = zone.rules.getOffset(i).totalSeconds

    /**
     * How much a DVR that does NOT follow daylight saving would have been off
     * by at `footage` time, compared to `checked` time. Same sign as the
     * calculator: negative = DVR fast, positive = DVR slow.
     */
    fun offsetShift(footage: Instant, checked: Instant): Int = offset(footage) - offset(checked)

    /** The clock change that matters between footage and checked, or null. */
    fun relevantChange(footage: Instant, checked: Instant): ClockChange? {
        if (!DaylightSavingStore.isEnabled) return null
        if (offsetShift(footage, checked) == 0) return null
        val list = changes(minOf(footage, checked), maxOf(footage, checked))
        return if (footage < checked) list.firstOrNull() else list.lastOrNull()
    }

    /** The most recent clock change within the last `days` days, or null. */
    fun recentChange(days: Int, now: Instant = Instant.now()): ClockChange? {
        if (!DaylightSavingStore.isEnabled) return null
        return changes(now.minusSeconds(days * 86400L), now).lastOrNull()
    }

    /** Whole days since `change`, counted by calendar date. */
    fun daysSince(change: ClockChange, now: Instant = Instant.now()): Int {
        val from = change.instant.atZone(zone).toLocalDate()
        val to = now.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(from, to).toInt()
    }

    /**
     * On fall-back day the hour before the change happens twice. If `i` is in
     * that hour, returns both moments that show the same clock time (first
     * time through, second time through). Null otherwise or when switched off.
     */
    fun repeatedHour(i: Instant): Pair<Instant, Instant>? {
        if (!DaylightSavingStore.isEnabled) return null
        fun sameClockReading(earlier: Instant) =
            offset(earlier) - offset(earlier.plusSeconds(3600)) == 3600
        if (sameClockReading(i)) return i to i.plusSeconds(3600)
        val hourBefore = i.minusSeconds(3600)
        if (sameClockReading(hourBefore)) return hourBefore to i
        return null
    }

    /**
     * What a clock reads `secondsToAdd` after real local time `i`, counted on
     * the clock face, so the result can't slip an hour across a clock change.
     */
    fun clockString(i: Instant, secondsToAdd: Int, pattern: String): String {
        val reading = i.plusSeconds((offset(i) + secondsToAdd).toLong())
        return DateTimeFormatter.ofPattern(pattern, Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(reading)
    }

    /** Every clock change in the phone's region between two moments, oldest first. */
    fun changes(start: Instant, end: Instant): List<ClockChange> {
        val result = mutableListOf<ClockChange>()
        val rules = zone.rules
        var cursor = start
        repeat(200) {
            val next = rules.nextTransition(cursor) ?: return result
            if (next.instant > end) return result
            val shift = next.offsetAfter.totalSeconds - next.offsetBefore.totalSeconds
            if (shift != 0) result.add(ClockChange(next.instant, shift))
            cursor = next.instant
        }
        return result
    }

    /** Formats a change's date as the clocks read just before it happened. */
    fun formatBeforeChange(i: Instant, pattern: String): String {
        val before = ZoneOffset.ofTotalSeconds(offset(i.minusSeconds(1)))
        return DateTimeFormatter.ofPattern(pattern, Locale.US).withZone(before).format(i)
    }
}

/** Retention Date wording, same rules as RetentionDateStore on iOS. */
object RetentionDate {
    /**
     * Under 3 months: total days only ("52 Days").
     * 3 months or more: Years/Months/Days ("4 Months, 3 Days").
     */
    fun durationString(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val start = minOf(date, today)
        val end = maxOf(date, today)
        val p = Period.between(start, end)
        if (p.years == 0 && p.months < 3) {
            val total = ChronoUnit.DAYS.between(start, end)
            return "$total Day${if (total == 1L) "" else "s"}"
        }
        val parts = mutableListOf<String>()
        if (p.years > 0) parts.add("${p.years} Year${if (p.years == 1) "" else "s"}")
        if (p.months > 0) parts.add("${p.months} Month${if (p.months == 1) "" else "s"}")
        if (p.days > 0 || parts.isEmpty()) parts.add("${p.days} Day${if (p.days == 1) "" else "s"}")
        return parts.joinToString(", ")
    }

    /** "November 4, 2026". */
    fun dateString(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
}
