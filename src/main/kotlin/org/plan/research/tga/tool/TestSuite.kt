package org.plan.research.tga.tool

import kotlinx.serialization.Serializable
import org.plan.research.tga.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.benchmark.json.PathAsStringSerializer
import java.nio.file.Path

@Serializable
data class TestSuite(
    @Serializable(with = PathAsStringSerializer::class)
    val testSrcPath: Path,
    val tests: List<String>,
    @Serializable(with = ListOfPathSerializer::class)
    val dependencies: List<Path>,
)
