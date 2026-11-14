package com.kirill.glassnotes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonthYearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Человекочитаемая дата для метки «создано». */
fun formatTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val date = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(dayMonthYearFormatter)
    }
}

/** Короткое отображение даты задачи в формате ISO (yyyy-MM-dd). */
fun formatDueDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""
    return try {
        LocalDate.parse(isoDate).format(dayMonthFormatter)
    } catch (e: Exception) {
        isoDate
    }
}

/** Конвертация epoch-миллисекунд в строку ISO (yyyy-MM-dd). */
fun millisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
