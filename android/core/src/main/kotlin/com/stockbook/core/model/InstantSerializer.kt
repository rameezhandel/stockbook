package com.stockbook.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Timestamps as ISO-8601, **truncated to whole seconds**.
 *
 * Not a style choice. The iOS build writes and reads this file with Foundation's
 * `.iso8601` strategy, which emits no fractional seconds and — more to the point
 * — *refuses to parse them*. A backup written on an Android phone with
 * millisecond precision would be rejected by the iPhone it was carried to, which
 * defeats the only way data moves between phones.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
