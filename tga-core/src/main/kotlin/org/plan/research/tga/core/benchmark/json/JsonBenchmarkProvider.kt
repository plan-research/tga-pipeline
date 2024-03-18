package org.plan.research.tga.core.benchmark.json

import kotlinx.serialization.json.Json
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.BenchmarkProvider
import java.nio.file.Path
import kotlin.io.path.readText

class JsonBenchmarkProvider(
    private val benchmarksFile: Path
) : BenchmarkProvider {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        prettyPrint = true
        useArrayPolymorphism = false
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
    }

    override fun benchmarks(): Collection<Benchmark> {
        return json.decodeFromString(
            benchmarksFile.readText()
        )
    }
}
