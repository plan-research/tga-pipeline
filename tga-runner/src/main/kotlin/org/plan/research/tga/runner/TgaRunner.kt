package org.plan.research.tga.runner

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.ClassCoverageInfo
import org.plan.research.tga.core.dependency.DependencyManager
import org.plan.research.tga.core.tool.protocol.BenchmarkRequest
import org.plan.research.tga.core.tool.protocol.SuccessfulGenerationResult
import org.plan.research.tga.core.tool.protocol.UnsuccessfulGenerationResult
import org.plan.research.tga.runner.coverage.jacoco.JacocoCoverageProvider
import org.plan.research.tga.runner.metrics.ClassMetrics
import org.plan.research.tga.runner.metrics.MetricsProvider
import org.plan.research.tga.runner.tool.protocol.tcp.TcpTgaServer
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration


@Serializable
data class ToolResults(
    val benchmark: Benchmark,
    val coverage: ClassCoverageInfo,
    val metrics: ClassMetrics
)

class TgaRunner(
    private val serverPort: UInt,
    private val configFile: Path,
    private val timeLimit: Duration,
    private val outputDirectory: Path,
    private val n: Int,
) {
    fun run() {
        val dependencyManager = DependencyManager()
        val benchmarkProvider = JsonBenchmarkProvider(configFile)
        val coverageProvider = JacocoCoverageProvider(dependencyManager)
        val metricsProvider = MetricsProvider(configFile.parent.resolve("metrics.json"))

        val server = TcpTgaServer(serverPort)
        log.debug("Started server, awaiting for tool connection")

        server.accept().use { toolConnection ->
            log.debug("Tool connected")

            val name = toolConnection.init()
            log.debug("Initialized tool with name $name")

            val baseDir = outputDirectory.resolve(name)

            for (run in 0 until n) {
                val runDir = baseDir.resolve("$run")

                val results = buildSet {
                    for (benchmark in benchmarkProvider.benchmarks()) {
                        log.debug("Running on benchmark ${benchmark.buildId}")

                        val benchmarkOutput = runDir.resolve(benchmark.buildId)
                        toolConnection.send(BenchmarkRequest(benchmark, timeLimit, benchmarkOutput))
                        log.debug("Sent benchmark to tool")

                        val result = toolConnection.receive()
                        log.debug("Received an answer from tool")
                        if (result is UnsuccessfulGenerationResult) {
                            log.error("Unsuccessful run on benchmark ${benchmark.buildId}: $result")
                            continue
                        }

                        val testSuite = (result as SuccessfulGenerationResult).testSuite

                        log.debug("Computing metrics")
                        val coverage = coverageProvider.computeCoverage(benchmark, testSuite)
                        val metrics = metricsProvider.getMetrics(benchmark)
                        log.debug(coverage)

                        add(ToolResults(benchmark, coverage, metrics))
                    }
                }

                val resultFile = runDir.resolve("results.json")
                resultFile.writeText(getJsonSerializer(pretty = true).encodeToString(results))
            }

            metricsProvider.save()
        }
    }
}
