package org.plan.research.tga.core.tool

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.dependency.Dependency
import java.nio.file.Path

@Serializable
data class TestSuite(
    /**
     * A path to the test sources directory
     */
    @Serializable(with = PathAsStringSerializer::class)
    val testSrcPath: Path,
    /**
     * Fully qualified test class names that compose a test suite
     */
    val tests: List<String>,
    /**
     * Fully qualified names of additional/utility classes that may be used by the test classes
     */
    val testSrcDependencies: List<String>,
    /**
     * External dependencies of the test suite, e.g., JUnit, Mockito, etc.
     */
    val dependencies: List<Dependency>,
)
