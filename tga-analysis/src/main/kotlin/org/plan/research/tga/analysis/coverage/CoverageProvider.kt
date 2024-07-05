package org.plan.research.tga.analysis.coverage

import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.TestSuiteCoverage
import org.plan.research.tga.core.tool.TestSuite
import java.nio.file.Path
import kotlin.io.path.readText

interface CoverageProvider {
    fun computeCoverage(benchmark: Benchmark, testSuite: TestSuite, compilationResult: CompilationResult): TestSuiteCoverage

    fun computeCoverage(benchmarkPath: Path, testSuitePath: Path, compilationResultPath: Path): TestSuiteCoverage {
        val serializer = getJsonSerializer(pretty = true)
        val benchmark = serializer.decodeFromString<Benchmark>(benchmarkPath.readText())
        val testSuite = serializer.decodeFromString<TestSuite>(testSuitePath.readText())
        val compilationResult = serializer.decodeFromString<CompilationResult>(compilationResultPath.readText())
        return computeCoverage(benchmark, testSuite, compilationResult)
    }
}
