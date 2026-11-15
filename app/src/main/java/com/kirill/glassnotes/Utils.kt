package com.kirill.glassnotes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val timestampFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.getDefault())
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Дата и время создания: «15.11.2026, 02:03». */
fun formatTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(timestampFormatter)
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

/** Русское склонение числа со словом: «1 заметка», «2 заметки», «5 заметок». */
fun pluralize(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$count $word"
}

/** Процентное отображение доли (0f..1f): 0.62f -> "62%". */
fun formatPercent(value: Float): String = "${(value * 100).roundToInt()}%"
