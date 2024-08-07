@file:Suppress("unused", "UnnecessaryOptInAnnotation", "ControlFlowWithEmptyBody", "UnusedImport")

package org.plan.research.tga.analysis

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.apache.commons.cli.Option
import org.plan.research.tga.analysis.compilation.TestSuiteCompiler
import org.plan.research.tga.analysis.coverage.jacoco.JacocoCliCoverageProvider
import org.plan.research.tga.analysis.junit.JUnitExternalRunner
import org.plan.research.tga.analysis.mutation.MutationScoreProvider
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.dependency.DependencyManager
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedWriter
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

val DOCKER_BENCHMARKS_DIR: Path = Paths.get("/var/benchmarks/gitbug")
val DOCKER_RESULTS_DIR: Path = Paths.get("/var/results")


/**
 * The things that we want to measure:
 * 1. Compilation rate
 * 2. Coverage
 * 3. Mutation score
 * 4. Feature coverage?
 *    * external dependencies
 *    * language
 * 5. Crash reproduction
 * 6. Readability...?
 * 7. ...?
 */


class TgaAnalysisConfig(args: Array<String>) : TgaConfig("tga-analysis", options, args) {
    companion object {
        private val options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option(null, "benchmarksPath", true, "path to local benchmarks")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "benchmarksPatchedPath", true, "path to local benchmarks")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "resultsPath", true, "path to results dir")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "threads", true, "number of threads to run")
                    .also { it.isRequired = false }
            )

            addOption(
                Option(null, "tool", true, "name of the tool")
                    .also { it.isRequired = true }
            )
        }
    }
}

@Serializable
data class Properties(
    val benchmark: String,
    val properties: Map<String, String>
)


@OptIn(ExperimentalPathApi::class, DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
    val config = TgaAnalysisConfig(args)
    val serializer = getJsonSerializer(pretty = true)
    val dependencyManager = DependencyManager()
    val compiler = TestSuiteCompiler(dependencyManager)
    val coverageProvider = JacocoCliCoverageProvider()

    val resultsDir = Paths.get(config.getCmdValue("resultsPath")!!)
    val benchmarksDir = Paths.get(config.getCmdValue("benchmarksPath")!!)
    val benchmarksPatched = JsonBenchmarkProvider(Paths.get(config.getCmdValue("benchmarksPatchedPath")!!))
        .benchmarks()
        .associateBy { it.buildId }
    val tools = resultsDir.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }
        .filter { it == config.getCmdValue("tool") }
    log.debug(tools)

    val threads = config.getCmdValue("threads")?.toInt() ?: 10
    val coroutineContext = newFixedThreadPoolContext(threads, "analysis-dispatcher")
    val benchmarkProperties = tryOrNull {
        getJsonSerializer(pretty = true)
            .decodeFromString<List<Properties>>(Paths.get("properties.json").readText())
            .associate { it.benchmark to it.properties }
    } ?: emptyMap()

    runBlocking(coroutineContext) {
        val allJobs = mutableListOf<Deferred<*>>()
        for (tool in tools) {
            val toolDir = resultsDir.resolve(tool)
            val runs = toolDir.listDirectoryEntries()
                .map { it.name }
                .groupBy({ it.substringBeforeLast('-') }, { it.substringAfterLast('-').toInt() })
                .mapValues { it.value.sorted() }

            log.debug(runs)
            for ((runName, iterations) in runs) {
                for (iteration in iterations) {
                    val allData = ConcurrentLinkedDeque<String>()
                    val runDir = toolDir.resolve("$runName-$iteration")
                    val benchmarks = runDir.listDirectoryEntries().map { it.name }
                    for (benchmarkName in benchmarks.sorted()) {
                        allJobs += async {
                            val benchmarkDir = runDir.resolve(benchmarkName)
                            if (!benchmarkDir.exists()) return@async

                            val benchmark = serializer.decodeFromString<Benchmark>(
                                benchmarkDir.walk().firstOrNull { it.name == "benchmark.json" }?.readText()
                                    ?: return@async
                            ).remap(DOCKER_BENCHMARKS_DIR, benchmarksDir)
                            val testSuite = serializer.decodeFromString<TestSuite>(
                                benchmarkDir.walk().firstOrNull { it.name == "testSuite.json" }?.readText()
                                    ?: return@async
                            ).remap(DOCKER_RESULTS_DIR, resultsDir)

                            if (!testSuite.testSrcPath.exists()) return@async

                            val compilationResult = compiler.compile(benchmark, testSuite)
                            val failures = JUnitExternalRunner().run(compilationResult)
                            testSuite.testSrcPath.resolve("failures.json").bufferedWriter().use {
                                it.write(serializer.encodeToString(failures))
                            }

                            benchmarksPatched[benchmark.buildId]?.let { patchedBenchmark ->
                                val patchedCompilationResult = compiler.compile(patchedBenchmark, testSuite)
                                val patchedFailures = JUnitExternalRunner().run(patchedCompilationResult)
                                testSuite.testSrcPath.resolve("failures-patched.json").bufferedWriter().use {
                                    it.write(serializer.encodeToString(patchedFailures))
                                }
                                patchedCompilationResult.compiledDir.deleteRecursively()
                            }

                            val coverage = coverageProvider.computeCoverage(benchmark, testSuite, compilationResult)
                            val mutationScore = MutationScoreProvider()
                                .computeMutationScore(benchmark, testSuite, compilationResult)

                            allData += String.format(
                                "%s, %s, %d, %s, %s, %.2f, %.2f, %.2f, %.2f, %s",
                                tool,
                                runName,
                                iteration,
                                benchmark.buildId,
                                benchmark.klass,
                                coverage.compilationRate.ratio * 100.0,
                                coverage.coverage.first().lines.ratio * 100.0,
                                coverage.coverage.first().branches.ratio * 100.0,
                                mutationScore.ratio * 100.0,
                                benchmarkProperties[benchmarkName]?.toList()
                                    ?.joinToString(", ") { "${it.first} -> ${it.second}" } ?: ""
                            )

                            compilationResult.compiledDir.deleteRecursively()
                        }
                    }
                    allJobs.awaitAll()
                    Paths.get("$tool-$iteration.csv").bufferedWriter().use {
                        it.write(allData.joinToString("\n"))
                    }
                }
            }
        }
    }
}
