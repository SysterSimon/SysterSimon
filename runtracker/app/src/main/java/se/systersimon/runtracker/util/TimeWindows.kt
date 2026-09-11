package se.systersimon.runtracker.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class EpochWindow(val from: Long, val to: Long)

fun currentWeekWindow(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): EpochWindow {
    val z = now.atZone(zone)
    val start = z.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(zone)
    return EpochWindow(start.toInstant().toEpochMilli(), start.plusWeeks(1).toInstant().toEpochMilli())
}

fun currentMonthWindow(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): EpochWindow {
    val z = now.atZone(zone)
    val start = ZonedDateTime.of(z.year, z.monthValue, 1, 0, 0, 0, 0, zone)
    return EpochWindow(start.toInstant().toEpochMilli(), start.plusMonths(1).toInstant().toEpochMilli())
}
