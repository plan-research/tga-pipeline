package org.plan.research.tga.benchmark

import kotlinx.serialization.Serializable
import org.plan.research.tga.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.benchmark.json.PathAsStringSerializer
import java.nio.file.Path

@Serializable
data class Benchmark(
    @Serializable(with = PathAsStringSerializer::class)
    val src: Path,
    @Serializable(with = ListOfPathSerializer::class)
    val classPath: List<Path>,
    val klass: String
)
