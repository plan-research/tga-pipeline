package org.plan.research.tga.core.benchmark.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.nio.file.Path

fun getJsonSerializer(pretty: Boolean): Json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = false
    prettyPrint = pretty
    useArrayPolymorphism = false
    allowStructuredMapKeys = true
    allowSpecialFloatingPointValues = true
}

object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toAbsolutePath().toString())

    override fun deserialize(decoder: Decoder): Path = Path.of(decoder.decodeString())
}

object ListOfPathSerializer : KSerializer<List<Path>> by ListSerializer(PathAsStringSerializer)
