package org.plan.research.tga.coverage

import org.plan.research.tga.benchmark.Benchmark
import org.plan.research.tga.tool.TestSuite

interface CoverageProvider {
    fun computeCoverage(benchmark: Benchmark, testSuite: TestSuite): ClassCoverageInfo
}
