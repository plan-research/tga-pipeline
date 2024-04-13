package org.plan.research.tga.runner.coverage

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.ClassCoverageInfo
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.CoverageProvider
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

class ExternalCoverageProvider(private val timeLimit: Duration) : CoverageProvider {
    private val json = getJsonSerializer(pretty = false)

    override fun computeCoverage(benchmark: Benchmark, testSuite: TestSuite): ClassCoverageInfo {
        val benchmarkStr = json.encodeToString(benchmark)
        val testSuiteStr = json.encodeToString(testSuite)
        val benchmarkPath = testSuite.testSrcPath.resolve("benchmark.json").also {
            it.parent.toFile().mkdirs()
            it.writeText(benchmarkStr)
        }
        val testSuitePath = testSuite.testSrcPath.resolve("testSuite.json").also {
            it.parent.toFile().mkdirs()
            it.writeText(testSuiteStr)
        }
        val output = testSuite.testSrcPath.resolve("coverage.json")

        runExternalCoverage(benchmarkPath, testSuitePath, output)

        return when {
            output.exists() -> json.decodeFromString<ClassCoverageInfo>(output.readText())
            else -> ClassCoverageInfo(ClassId(benchmark.klass), emptySet())
        }
    }

    private fun runExternalCoverage(benchmark: Path, testSuite: Path, output: Path) {
        val args = buildString {
            append("--benchmark ${benchmark.toAbsolutePath()} ")
            append("--testSuite ${testSuite.toAbsolutePath()} ")
            append("--output ${output.toAbsolutePath()}")
        }
        val builder = ProcessBuilder(
            "/bin/sh", "gradlew", "tga-runner:runCoverage", "--args=$args",
        )
        builder.directory(TGA_PIPELINE_HOME.toFile())
        log.debug("Running coverage computation with command \"${builder.command().joinToString(" ")}\"")
        val process = builder.start()
        process.waitFor(timeLimit.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        process.destroy()
    }
}
