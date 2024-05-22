package org.plan.research.tga.core.benchmark

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.plan.research.tga.core.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.util.remap
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
) {
    fun remap(from: Path, to: Path): Benchmark = copy(
        root = root.remap(from, to),
        src = src.remap(from, to),
        bin = bin.remap(from, to),
        classPath = classPath.map { it.remap(from, to) }
    )
}
