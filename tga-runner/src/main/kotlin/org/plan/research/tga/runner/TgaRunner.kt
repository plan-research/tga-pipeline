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
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.time.Duration


data class ToolResults(
    val benchmark: Benchmark,
    val coverage: ClassCoverageInfo,
    val metrics: ClassMetrics
)

class TgaRunner(
    private val serverPort: UInt,
    private val configFile: Path,
    private val timeLimit: Duration,
    private val outputDirectory: Path
) {
    fun run(): Set<ToolResults> = buildSet {
        val benchmarkProvider = JsonBenchmarkProvider(configFile)
        val coverageBuilder = JacocoCoverageProvider()

        val server = TcpTgaServer(serverPort)
        log.debug("Started server, awaiting for tool connection")

        val toolConnection = server.accept()
        log.debug("Tool connected")

        for (benchmark in benchmarkProvider.benchmarks()) {
            log.debug("Running on benchmark ${benchmark.buildId}")

            val benchmarkOutput = outputDirectory.resolve(benchmark.buildId)
            toolConnection.send(BenchmarkRequest(benchmark, timeLimit, benchmarkOutput))
            log.debug("Sent benchmark to tool")

            val result = toolConnection.receive()
            log.debug("Received an answer from tool")
            if (result is UnsuccessfulGenerationResult) {
                log.error("Unsuccessful run on benchmark ${benchmark.buildId}: $result")
            }

            val testSuite = (result as SuccessfulGenerationResult).testSuite

            log.debug("Computing metrics")
            val coverage = coverageBuilder.computeCoverage(benchmark, testSuite)
            val metrics = computeMetrics(benchmark)
            log.debug(coverage)

            add(ToolResults(benchmark, coverage, metrics))
        }
    }
}
