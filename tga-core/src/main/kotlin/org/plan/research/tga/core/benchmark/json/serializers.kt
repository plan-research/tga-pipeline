package org.plan.research.tga.core.benchmark.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.Id
import org.plan.research.tga.core.coverage.InstructionId
import org.plan.research.tga.core.coverage.LineId
import org.plan.research.tga.core.coverage.MethodId
import java.nio.file.Path

fun getJsonSerializer(pretty: Boolean): Json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = false
    prettyPrint = pretty
    useArrayPolymorphism = false
    allowStructuredMapKeys = true
    allowSpecialFloatingPointValues = true
    serializersModule = SerializersModule {
        polymorphic(Id::class) {
            this.subclass(InstructionId::class, InstructionId.serializer())
            this.subclass(LineId::class, LineId.serializer())
            this.subclass(BranchId::class, BranchId.serializer())
            this.subclass(MethodId::class, MethodId.serializer())
            this.subclass(ClassId::class, ClassId.serializer())
        }
    }
}

object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toAbsolutePath().toString())

    override fun deserialize(decoder: Decoder): Path = Path.of(decoder.decodeString())
}

object ListOfPathSerializer : KSerializer<List<Path>> by ListSerializer(PathAsStringSerializer)
