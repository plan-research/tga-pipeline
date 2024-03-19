package org.plan.research.tga.tool

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.protocol.GenerationResult
import org.plan.research.tga.core.tool.protocol.SuccessfulGenerationResult
import org.plan.research.tga.core.tool.protocol.Tool2TgaConnection
import org.plan.research.tga.core.tool.protocol.UnsuccessfulGenerationResult
import kotlin.concurrent.thread


class ToolController(
    private val connection: Tool2TgaConnection,
    val toolCreator: () -> TestGenerationTool,
) {
    fun run() = connection.use {
        while (true) {
            val request = connection.receive()
            val tool = toolCreator()

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
            } catch (e: Throwable) {
                result = UnsuccessfulGenerationResult(e.stackTraceToString())
            }

            connection.send(result)
        }
    }
}
