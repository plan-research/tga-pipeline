package org.plan.research.tga.core.coverage

import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.tool.TestSuite

interface CoverageProvider {
    fun computeCoverage(benchmark: Benchmark, testSuite: TestSuite): TestSuiteCoverage
}
