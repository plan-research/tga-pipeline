package org.plan.research.tga.analysis.coverage

import kotlinx.serialization.encodeToString
import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.Fraction
import org.plan.research.tga.core.coverage.TestSuiteCoverage
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.vorpal.research.kthelper.getJavaPath
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import org.vorpal.research.kthelper.terminate
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Deprecated("It is basically not needed, considering that we already run jacoco externally")
class ExternalCoverageProvider(private val timeLimit: Duration) : CoverageProvider {
    private val json = getJsonSerializer(pretty = false)

    override fun computeCoverage(
        benchmarkPath: Path,
        testSuitePath: Path,
        compilationResultPath: Path
    ): TestSuiteCoverage = try {
        val output = benchmarkPath.parent.resolve("coverage.json")
        runExternalCoverage(benchmarkPath, testSuitePath, compilationResultPath, output)

        when {
            output.exists() -> json.decodeFromString<TestSuiteCoverage>(output.readText())
            else -> TestSuiteCoverage(Fraction(0, 0), emptySet())
        }
    } catch (err: Exception) {
        log.error(
            "Execution of ExternalCoverageProvider.computeCoverage function failed due to the error: ${err.message}",
            err
        )
        TestSuiteCoverage(Fraction(0, 0), emptySet())
    }

    override fun computeCoverage(
        benchmark: Benchmark,
        testSuite: TestSuite,
        compilationResult: CompilationResult
    ): TestSuiteCoverage = try {
        val benchmarkStr = json.encodeToString(benchmark)
        val testSuiteStr = json.encodeToString(testSuite)
        val compilationResultStr = json.encodeToString(compilationResult)
        val benchmarkPath = testSuite.testSrcPath.resolve("benchmark.json").also {
            it.parent.toFile().mkdirs()
            it.writeText(benchmarkStr)
        }
        val testSuitePath = testSuite.testSrcPath.resolve("testSuite.json").also {
            it.parent.toFile().mkdirs()
            it.writeText(testSuiteStr)
        }
        val compilationResultPath = testSuite.testSrcPath.resolve("compilationResult.json").also {
            it.parent.toFile().mkdirs()
            it.writeText(compilationResultStr)
        }
        val output = testSuite.testSrcPath.resolve("coverage.json")

        runExternalCoverage(benchmarkPath, testSuitePath, compilationResultPath, output)

        log.debug("'coverage.json' exists: ${output.exists()}")

        when {
            output.exists() -> json.decodeFromString<TestSuiteCoverage>(output.readText())
            else -> TestSuiteCoverage(Fraction(0, testSuite.tests.size), emptySet())
        }
    } catch (err: Exception) {
        log.error(
            "Execution of ExternalCoverageProvider.computeCoverage function failed due to the error: ${err.message}",
            err
        )
        TestSuiteCoverage(Fraction(0, testSuite.tests.size), emptySet())
    }

    private fun runExternalCoverage(benchmark: Path, testSuite: Path, compilationResultPath: Path, output: Path) {
        val builder = ProcessBuilder(
            getJavaPath().toString(),
            "-jar",
            TGA_PIPELINE_HOME.resolve("tga-runner", "build", "libs", "tga-coverage.jar").toString(),
            "--benchmark",
            benchmark.toAbsolutePath().toString(),
            "--testSuite",
            testSuite.toAbsolutePath().toString(),
            "--compilationResult",
            compilationResultPath.toAbsolutePath().toString(),
            "--output",
            output.toAbsolutePath().toString(),
        )
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        builder.directory(TGA_PIPELINE_HOME.toFile())
        log.debug("Running coverage computation with command \"${builder.command().joinToString(" ")}\"")
        val process = builder.start()
        process.waitFor(timeLimit.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        process.terminate(10U, 1.seconds)
    }
}
