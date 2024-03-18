package org.plan.research.tga.runner

import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.coverage.ClassCoverageInfo
import org.plan.research.tga.core.tool.protocol.BenchmarkRequest
import org.plan.research.tga.core.tool.protocol.SuccessfulGenerationResult
import org.plan.research.tga.core.tool.protocol.UnsuccessfulGenerationResult
import org.plan.research.tga.runner.coverage.jacoco.JacocoCoverageProvider
import org.plan.research.tga.runner.metrics.ClassMetrics
import org.plan.research.tga.runner.metrics.computeMetrics
import org.plan.research.tga.runner.tool.protocol.tcp.TcpTgaServer
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.time.Duration


data class ToolResults(
    val benchmark: Benchmark,
    val coverage: ClassCoverageInfo,
    val metrics: ClassMetrics
)

class TgaRunner(
    val serverPort: UInt,
    val configFile: Path,
    val timeLimit: Duration,
    val outputDirectory: Path
) {
    fun run(): Set<ToolResults> = buildSet {
        val benchmarkProvider = JsonBenchmarkProvider(configFile)
        val coverageBuilder = JacocoCoverageProvider()

        val server = TcpTgaServer(serverPort)
        val toolConnection = server.accept()
        for (benchmark in benchmarkProvider.benchmarks()) {
            val benchmarkOutput = outputDirectory.resolve(benchmark.buildId)
            toolConnection.send(BenchmarkRequest(benchmark, timeLimit, benchmarkOutput))

            val result = toolConnection.receive()
            if (result is UnsuccessfulGenerationResult) {
                log.error("Unsuccessful run on benchmark ${benchmark.buildId}: $result")
            }

            val testSuite = (result as SuccessfulGenerationResult).testSuite

            val coverage = coverageBuilder.computeCoverage(benchmark, testSuite)
            val metrics = computeMetrics(benchmark)
            add(ToolResults(benchmark, coverage, metrics))
        }
    }
}
