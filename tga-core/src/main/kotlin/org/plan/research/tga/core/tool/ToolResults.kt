package org.plan.research.tga.core.tool

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.coverage.ClassCoverageInfo
import kotlin.time.Duration

@Serializable
data class ToolResults(
    val benchmark: Benchmark,
    val generationTime: Duration,
    val testSuite: TestSuite,
    val coverage: ClassCoverageInfo,
)
