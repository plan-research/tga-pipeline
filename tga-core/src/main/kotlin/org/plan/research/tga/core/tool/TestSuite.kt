package org.plan.research.tga.core.tool

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.dependency.Dependency
import java.nio.file.Path

@Serializable
data class TestSuite(
    @Serializable(with = PathAsStringSerializer::class)
    val testSrcPath: Path,
    val tests: List<String>,
    @Serializable(with = ListOfPathSerializer::class)
    val dependencies: List<Dependency>,
)
