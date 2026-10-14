package org.openmrs.chwsync.android.util

import java.util.UUID

/**
 * Thin wrapper around [java.util.UUID] so call sites read clearly and so the
 * wire format (canonical lowercase hyphenated string) is centralised.
 */
object Uuid {
    fun random(): String = UUID.randomUUID().toString()

    fun isValid(value: String?): Boolean =
        value != null && runCatching { UUID.fromString(value) }.isSuccess
}
