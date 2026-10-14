package org.openmrs.chwsync.android.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatting helpers for the wire date/time formats used by the OpenMRS CHW
 * module. Payload datetimes are always local wall-clock values in the
 * `yyyy-MM-dd'T'HH:mm:ss` form; audit timestamps from the server are ISO-8601
 * instants that may carry an offset.
 */
object WireDate {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Current local date-time in wire format, suitable for payload fields. */
    fun now(): String = LocalDateTime.now().format(DATE_TIME)

    fun formatDate(value: LocalDate): String = value.format(DATE)

    fun formatDateTime(value: LocalDateTime): String = value.format(DATE_TIME)

    fun parseDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun parseDateTime(value: String?): LocalDateTime? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDateTime.parse(it) }.getOrNull()
        }

    /** Parses a server ISO timestamp; tolerates both offset and `Z` forms. */
    fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { OffsetDateTime.parse(raw).toInstant() }
                .recoverCatching { Instant.parse(raw) }
                .getOrNull()
        }

    /** Human readable rendering for the UI; falls back to the raw string. */
    fun display(value: String?): String {
        if (value.isNullOrBlank()) return ""
        parseInstant(value)?.let {
            return DISPLAY.withZone(ZoneId.systemDefault()).format(it)
        }
        parseDateTime(value)?.let {
            return it.format(DISPLAY)
        }
        return value
    }

    fun nowInstantIso(): String = Instant.now().toString()
}
