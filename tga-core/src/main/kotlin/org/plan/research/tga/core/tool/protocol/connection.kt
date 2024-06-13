package org.plan.research.tga.core.tool.protocol

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.tool.TestSuite
import java.nio.file.Path
import kotlin.time.Duration

@Serializable
sealed interface GenerationRequest

@Serializable
data class BenchmarkRequest(
    val benchmark: Benchmark,
    val timeLimit: Duration,
    @Serializable(with = PathAsStringSerializer::class)
    val outputDirectory: Path,
) : GenerationRequest

@Serializable
data object StopRequest : GenerationRequest

@Serializable
sealed interface GenerationResult {
    val generationTime: Duration
}

@Serializable
data class SuccessfulGenerationResult(
    val testSuite: TestSuite,
    override val generationTime: Duration
) : GenerationResult

@Serializable
data class UnsuccessfulGenerationResult(
    val reason: String,
    override val generationTime: Duration
) : GenerationResult

val protocolJson = getJsonSerializer(pretty = false)

interface TgaServer : AutoCloseable {
    fun accept(): Tga2ToolConnection
}

interface Tga2ToolConnection : AutoCloseable {
    fun init(): String
    fun send(request: GenerationRequest)
    fun receive(): GenerationResult
}

interface Tool2TgaConnection : AutoCloseable {
    fun init(name: String)
    fun receive(): GenerationRequest
    fun send(result: GenerationResult)
}
