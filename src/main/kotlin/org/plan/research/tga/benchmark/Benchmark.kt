package org.plan.research.tga.benchmark

import kotlinx.serialization.Serializable
import org.plan.research.tga.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.benchmark.json.PathAsStringSerializer
import java.nio.file.Path

@Serializable
data class Benchmark(
    val name: String,
    @Serializable(with = PathAsStringSerializer::class)
    val root: Path,
    val build_id: String,
    @Serializable(with = ListOfPathSerializer::class)
    val classPath: List<Path>,
    val klass: String,
)
