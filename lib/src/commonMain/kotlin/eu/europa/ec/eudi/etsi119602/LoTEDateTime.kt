package eu.europa.ec.eudi.etsi119602

import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.parse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant


public typealias LoTEDateTime = @Serializable(with = LoTEDateTimeSerializer::class) Instant


public object LoTEDateTimeSerializer : KSerializer<Instant> {
    private val isoFormat =
        DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LoTEDateTimeSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        val str = value.format(isoFormat)
        encoder.encodeString(str)
    }

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeString()
        return Instant.parse(str, isoFormat)
    }
}