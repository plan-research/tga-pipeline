package org.plan.research.tga.core.tool.protocol

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.tool.TestSuite
import java.nio.file.Path
import kotlin.time.Duration

@Serializable
data class BenchmarkRequest(
    val benchmark: Benchmark,
    val timeLimit: Duration,
    @Serializable(with = PathAsStringSerializer::class)
    val outputDirectory: Path,
)

@Serializable
sealed interface GenerationResult

@Serializable
data class SuccessfulGenerationResult(val testSuite: TestSuite) : GenerationResult

@Serializable
data class UnsuccessfulGenerationResult(val reason: String) : GenerationResult

val protocolJson = getJsonSerializer(pretty = false)

interface TgaServer : AutoCloseable {
    fun accept(): Tga2ToolConnection
}

interface Tga2ToolConnection : AutoCloseable {
    fun init(): String
    fun send(request: BenchmarkRequest)
    fun receive(): GenerationResult
}

interface Tool2TgaConnection : AutoCloseable {
    fun init(name: String)
    fun receive(): BenchmarkRequest
    fun send(result: GenerationResult)
}
