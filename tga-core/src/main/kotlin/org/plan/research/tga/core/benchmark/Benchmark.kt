package org.plan.research.tga.core.benchmark

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.plan.research.tga.core.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Benchmark(
    val name: String,
    @Serializable(with = PathAsStringSerializer::class)
    val root: Path,
    @JsonNames("build_id", "buildId")
    val buildId: String,
    @Serializable(with = PathAsStringSerializer::class)
    val src: Path,
    @Serializable(with = PathAsStringSerializer::class)
    val bin: Path,
    @Serializable(with = ListOfPathSerializer::class)
    val classPath: List<Path>,
    val klass: String,
)
