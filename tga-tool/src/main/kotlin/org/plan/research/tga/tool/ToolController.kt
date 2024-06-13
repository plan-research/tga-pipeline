package org.plan.research.tga.tool

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.protocol.BenchmarkRequest
import org.plan.research.tga.core.tool.protocol.GenerationResult
import org.plan.research.tga.core.tool.protocol.StopRequest
import org.plan.research.tga.core.tool.protocol.SuccessfulGenerationResult
import org.plan.research.tga.core.tool.protocol.Tool2TgaConnection
import org.vorpal.research.kthelper.logging.log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime


class ToolController(
    private val connection: Tool2TgaConnection,
    private val tool: TestGenerationTool,
) {
    private val executorService = Executors.newSingleThreadScheduledExecutor()

    fun run() = connection.use {
        connection.init(tool.name)
        while (true) {
            val request = try {
                connection.receive()
            } catch (e: Throwable) {
                log.error("Failed to receive a request from the server: ", e)
                break
            }
            when (request) {
                is StopRequest -> {
                    log.debug("Stopping tool ${tool.name}")
                    break
                }
                is BenchmarkRequest -> {
                    tool.init(request.benchmark.root, request.benchmark.classPath)

                    val hardTimeout = request.timeLimit * 2
                    val generationTime = measureTime {
                        val execution = executorService.submit {
                            tool.run(
                                request.benchmark.klass,
                                request.timeLimit,
                                request.outputDirectory
                            )
                        }
                        try {
                            execution.get(hardTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                        } catch (_: Throwable) {
                        } finally {
                            execution.cancel(true)
                        }
                    }
                    val result: GenerationResult = SuccessfulGenerationResult(
                        tool.report(),
                        generationTime
                    )

                    try {
                        connection.send(result)
                    } catch (e: Throwable) {
                        log.error("Failed to send a response to the server: ", e)
                        break
                    }
                }
            }
        }
    }

    fun shutdown() {
        executorService.shutdown()
    }
}
