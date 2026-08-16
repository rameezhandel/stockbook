package com.stockbook.core.model

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The clock, at the precision the file can hold.
 *
 * `Instant.now()` carries nanoseconds; the backup format carries whole seconds,
 * because that is what Foundation's `.iso8601` writes and all it will parse. Left
 * alone, a bill saved at 09:41:07.705 would come back from its own file as
 * 09:41:07 — the value in memory and the value on disk quietly disagreeing, and
 * every timestamp shifting a fraction on the first relaunch.
 *
 * Truncating at the moment of creation means what you hold is what you saved.
 */
object Timestamps {
    fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
}
