package org.plan.research.tga.runner

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.tool.protocol.BenchmarkRequest
import org.plan.research.tga.core.tool.protocol.StopRequest
import org.plan.research.tga.core.tool.protocol.SuccessfulGenerationResult
import org.plan.research.tga.core.tool.protocol.UnsuccessfulGenerationResult
import org.plan.research.tga.runner.tool.protocol.tcp.TcpTgaServer
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration

class TgaRunner(
    private val serverPort: UInt,
    private val configFile: Path,
    private val timeLimit: Duration,
    private val outputDirectory: Path,
    private val baseRunName: String,
    private val runIds: IntRange,
) {
    private val json = getJsonSerializer(pretty = false)

    fun run() {
        val benchmarkProvider = JsonBenchmarkProvider(configFile)
        val server = TcpTgaServer(serverPort)
        log.debug("Started server, awaiting for tool connection")

        server.accept().use { toolConnection ->
            log.debug("Tool connected")

            val name = toolConnection.init()
            log.debug("Initialized tool with name $name")

            val baseDir = outputDirectory.resolve(name)

            for (run in runIds) {
                val runDir = baseDir.resolve("$baseRunName-$run")
                for (benchmark in benchmarkProvider.benchmarks()) {
                    log.debug("Running on benchmark ${benchmark.buildId}")

                    val benchmarkOutput = runDir.resolve(benchmark.buildId)

                    if (benchmarkOutput.exists()) {
                        log.debug("Benchmark {} already run, skipping", benchmark)
                        continue
                    }

                    toolConnection.send(BenchmarkRequest(benchmark, timeLimit, benchmarkOutput))
                    log.debug("Sent benchmark to tool")

                    val result = toolConnection.receive()
                    log.debug("Received an answer from tool")
                    log.debug("Generation time is ${result.generationTime}")
                    if (result is UnsuccessfulGenerationResult) {
                        log.error("Unsuccessful run on benchmark ${benchmark.buildId}: $result")
                        continue
                    }
                    val testSuite = (result as SuccessfulGenerationResult).testSuite

                    if (!benchmarkOutput.exists()) {
                        log.error("Tool $name did not produce a test suite for ${benchmark.buildId} during run $run")
                        continue
                    }

                    benchmarkOutput.resolve("benchmark.json").also {
                        it.parent.toFile().mkdirs()
                        it.writeText(json.encodeToString(benchmark))
                    }
                    benchmarkOutput.resolve("testSuite.json").also {
                        it.parent.toFile().mkdirs()
                        it.writeText(json.encodeToString(testSuite))
                    }

                    Files.walk(benchmarkOutput).forEach {
                        it.toFile().setReadable(true, false)
                        it.toFile().setWritable(true, false)
                    }
                }
            }

            toolConnection.send(StopRequest)
        }

        Files.walk(outputDirectory).forEach {
            it.toFile().setReadable(true, false)
            it.toFile().setWritable(true, false)
        }
    }
}
