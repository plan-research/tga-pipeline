@file:Suppress("unused", "UnnecessaryOptInAnnotation", "ControlFlowWithEmptyBody")

package org.plan.research.tga.analysis

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import org.plan.research.tga.analysis.compilation.TestSuiteCompiler
import org.plan.research.tga.analysis.coverage.jacoco.JacocoCliCoverageProvider
import org.plan.research.tga.analysis.mutation.MutationScoreProvider
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.dependency.DependencyManager
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

val DOCKER_BENCHMARKS_DIR: Path = Paths.get("/var/benchmarks/gitbug")
val LOCAL_BENCHMARKS_DIR: Path = Paths.get("/home/abdullin/workspace/tga-pipeline/benchmarks")
val DOCKER_RESULTS_DIR: Path = Paths.get("/var/results")
val LOCAL_RESULTS_DIR: Path = Paths.get("/home/abdullin/workspace/tga-pipeline/results")


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


@OptIn(ExperimentalPathApi::class, DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
    val serializer = getJsonSerializer(pretty = true)
    val dependencyManager = DependencyManager()
    val compiler = TestSuiteCompiler(dependencyManager)
    val coverageProvider = JacocoCliCoverageProvider()

    val resultsDir = Paths.get(args[0])
    val tools = resultsDir.listDirectoryEntries().map { it.name }
    log.debug(tools)

    val coroutineContext = newFixedThreadPoolContext(1, "analysis-dispatcher")

    val allData = ConcurrentLinkedDeque<String>()
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
                    val runDir = toolDir.resolve("$runName-$iteration")
                    val benchmarks = runDir.listDirectoryEntries().map { it.name }
                    for (benchmarkName in benchmarks.sorted()) {
                        allJobs += async {
                            val benchmarkDir = runDir.resolve(benchmarkName)
                            if (!benchmarkDir.exists()) return@async

                            val benchmark = serializer.decodeFromString<Benchmark>(
                                benchmarkDir.walk().firstOrNull { it.name == "benchmark.json" }?.readText()
                                    ?: return@async
                            ).remap(DOCKER_BENCHMARKS_DIR, LOCAL_BENCHMARKS_DIR)
                            val testSuite = serializer.decodeFromString<TestSuite>(
                                benchmarkDir.walk().firstOrNull { it.name == "testSuite.json" }?.readText()
                                    ?: return@async
                            ).remap(DOCKER_RESULTS_DIR, LOCAL_RESULTS_DIR)

                            if (!testSuite.testSrcPath.exists()) return@async

                            val compilationResult = compiler.compile(benchmark, testSuite)

                            val coverage = coverageProvider.computeCoverage(benchmark, testSuite, compilationResult)
                            val mutationScore = MutationScoreProvider().computeMutationScore(benchmark, testSuite, compilationResult)

                            allData += String.format(
                                "%s, %s, %d, %s, %s, %.2f, %.2f, %.2f, %.2f",
                                tool,
                                runName,
                                iteration,
                                benchmark.buildId,
                                benchmark.klass,
                                coverage.compilationRate.ratio * 100.0,
                                coverage.coverage.first().lines.ratio * 100.0,
                                coverage.coverage.first().branches.ratio * 100.0,
                                mutationScore.ratio * 100.0,
                            )

                            compilationResult.compiledDir.deleteRecursively()
                        }
                    }
                }
            }
        }
        allJobs.awaitAll()

        log.debug(allData.joinToString("\n"))
    }
}
