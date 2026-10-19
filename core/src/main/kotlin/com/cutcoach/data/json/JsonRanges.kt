package com.cutcoach.data.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int

/**
 * The protocol writes some numbers as a single value and others as a two-value
 * range, sometimes in the same field. Sunday's `duration_min` is `[60, 75]`
 * while Monday's is `40`. Rather than bend the protocol to suit the code, these
 * two types bend the code to suit the protocol.
 */

/**
 * A number that may be a single value or a low-to-high range.
 *
 * `40` becomes min 40, max 40. `[60, 75]` becomes min 60, max 75.
 */
@Serializable(with = NumRangeSerializer::class)
data class NumRange(val min: Double, val max: Double) {

    /** True when the protocol gave one number, not a range. */
    val isSingle: Boolean get() = min == max

    val minInt: Int get() = min.toInt()
    val maxInt: Int get() = max.toInt()

    /** "40" for a single value, "60 to 75" for a range. */
    fun describe(unit: String = ""): String {
        val suffix = if (unit.isEmpty()) "" else " $unit"
        return if (isSingle) "${format(min)}$suffix" else "${format(min)} to ${format(max)}$suffix"
    }

    operator fun contains(value: Double): Boolean = value in min..max

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    companion object {
        fun of(value: Int) = NumRange(value.toDouble(), value.toDouble())
    }
}

internal object NumRangeSerializer : KSerializer<NumRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NumRange")

    override fun deserialize(decoder: Decoder): NumRange {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("NumRange can only be read from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                val v = element.doubleOrNull
                    ?: throw SerializationException("Expected a number, got $element")
                NumRange(v, v)
            }
            is JsonArray -> {
                val numbers = element.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                if (numbers.size != 2) {
                    throw SerializationException("Expected a two-number range, got $element")
                }
                NumRange(numbers[0], numbers[1])
            }
            else -> throw SerializationException("Expected a number or a range, got $element")
        }
    }

    override fun serialize(encoder: Encoder, value: NumRange) {
        throw SerializationException("The protocol files are read-only; nothing writes them back")
    }
}

/**
 * A span of nights in the bedtime ramp. The last step is written `[15, null]`,
 * meaning night 15 onwards with no end.
 */
@Serializable(with = NightSpanSerializer::class)
data class NightSpan(val from: Int, val to: Int?) {

    fun contains(night: Int): Boolean = night >= from && (to == null || night <= to)

    /** "Nights 1 to 3", or "Night 15 onwards" for the open-ended final step. */
    fun describe(): String = when {
        to == null -> "Night $from onwards"
        to == from -> "Night $from"
        else -> "Nights $from to $to"
    }
}

internal object NightSpanSerializer : KSerializer<NightSpan> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NightSpan")

    override fun deserialize(decoder: Decoder): NightSpan {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("NightSpan can only be read from JSON")
        val element = input.decodeJsonElement() as? JsonArray
            ?: throw SerializationException("Expected a [from, to] pair")
        if (element.size != 2) throw SerializationException("Expected a [from, to] pair, got $element")

        val from = (element[0] as? JsonPrimitive)?.int
            ?: throw SerializationException("Night span must start with a number")
        val to = element[1].let { if (it is JsonNull) null else (it as? JsonPrimitive)?.int }
        return NightSpan(from, to)
    }

    override fun serialize(encoder: Encoder, value: NightSpan) {
        throw SerializationException("The protocol files are read-only; nothing writes them back")
    }
}
