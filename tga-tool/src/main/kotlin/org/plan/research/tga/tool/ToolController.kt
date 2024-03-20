package org.plan.research.tga.tool

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.protocol.GenerationResult
import org.plan.research.tga.core.tool.protocol.SuccessfulGenerationResult
import org.plan.research.tga.core.tool.protocol.Tool2TgaConnection
import org.plan.research.tga.core.tool.protocol.UnsuccessfulGenerationResult
import org.vorpal.research.kthelper.logging.log
import kotlin.concurrent.thread


class ToolController(
    private val connection: Tool2TgaConnection,
    val tool: TestGenerationTool,
) {
    fun run() = connection.use {
        connection.init(tool.name)
        while (true) {
            val request = try {
                connection.receive()
            } catch (e: Throwable) {
                log.error("Failed to receive a request from the server: ", e)
                break
            }

            tool.init(request.benchmark.root, request.benchmark.classPath)

            val hardTimeout = request.timeLimit * 2
            var result: GenerationResult = UnsuccessfulGenerationResult("Uninitialized")
            val execution = thread {
                result = SuccessfulGenerationResult(
                    tool.run(
                        request.benchmark.klass,
                        request.timeLimit,
                        request.outputDirectory
                    )
                )
            }
            try {
                execution.join(hardTimeout.inWholeMilliseconds)
                execution.interrupt()
            } catch (e: Throwable) {
                result = UnsuccessfulGenerationResult(e.stackTraceToString())
            }

            try {
                connection.send(result)
            } catch (e: Throwable) {
                log.error("Failed to send a response to the server: ", e)
                break
            }
        }
    }
}
