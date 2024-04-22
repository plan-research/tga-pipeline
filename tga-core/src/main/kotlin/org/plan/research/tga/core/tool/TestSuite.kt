package org.plan.research.tga.core.tool

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.dependency.Dependency
import java.nio.file.Path

// TODO: remove all the unnecessary fields after the fix of TestSpark
@Serializable
data class TestSuite(
    @Serializable(with = PathAsStringSerializer::class)
    val testSrcPath: Path,
    val tests: List<String>,
    val testCasesOnly: List<String>,
    val testSuiteQualifiedName: String,
    val dependencies: List<Dependency>,
)
