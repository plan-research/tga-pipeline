package org.plan.research.tga.analysis.mutation

import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.coverage.Fraction
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import org.vorpal.research.kthelper.terminateOrKill
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MutationScoreProvider {
    companion object {
        private val PI_TEST_PATH = TGA_PIPELINE_HOME.resolve("lib", "pitest.jar")
    }

    fun computeMutationScore(
        benchmark: Benchmark,
        testSuite: TestSuite,
        compilationResult: CompilationResult
    ): Fraction {
        log.debug("Running mutation score computation for {}", testSuite.testSrcPath)
        val pitestLog = testSuite.testSrcPath.resolve("pitest.log")
        val cp = compilationResult.fullClassPath
            .filterNot { "junit-platform" in it.toString() }
            .filterNot { "junit-jupiter" in it.toString() }
        val process = buildProcess(
            "java",
            "-cp",
            PI_TEST_PATH.toAbsolutePath().toString(),
            "org.pitest.mutationtest.commandline.MutationCoverageReport",
            "--includeLaunchClasspath", "false",
            "--classPath",
            cp.joinToString(","),
            "--reportDir", testSuite.testSrcPath.toAbsolutePath().toString(),
            "--targetClasses", benchmark.klass,
            "--targetTests", compilationResult.compilableTests.keys.joinToString(","),
            "--sourceDirs", benchmark.src.toAbsolutePath().toString(),
            "--outputFormats", "CSV"
        ) {
            this.redirectErrorStream(true)
            this.redirectOutput(pitestLog.toFile())
        }
        process.waitFor(100.seconds.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        process.terminateOrKill(10U, waitTime = 500.milliseconds)

        log.debug("Mutation log: {}", pitestLog.readText())
        val mutationsFile = testSuite.testSrcPath.resolve("mutations.csv")
        val mutationsInfo = when {
            mutationsFile.exists() -> {
                mutationsFile.readLines()
            }

            else -> {
                emptyList()
            }
        }
        return Fraction(mutationsInfo.count { "KILLED" in it }, mutationsInfo.size)
    }
}
