@file:Suppress("unused", "UNUSED_VARIABLE")

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
        for (tool in tools.takeLast(1)) {
            val toolDir = resultsDir.resolve(tool)
            val runs = toolDir.listDirectoryEntries()
                .map { it.name }
                .groupBy({ it.substringBeforeLast('-') }, { it.substringAfterLast('-').toInt() })
                .mapValues { it.value.sorted() }

            log.debug(runs)
            for ((runName, iterations) in runs) {
                for (iteration in iterations.take(1)) {
                    val runDir = toolDir.resolve("$runName-$iteration")
                    val benchmarks = runDir.listDirectoryEntries().map { it.name }
                    for (benchmarkName in benchmarks.shuffled().take(10)) {
                        allJobs += async {
                            val benchmarkDir = runDir.resolve(benchmarkName)
                            val benchmark = serializer.decodeFromString<Benchmark>(
                                benchmarkDir.walk().firstOrNull { it.name == "benchmark.json" }?.readText()
                                    ?: return@async
                            ).remap(DOCKER_BENCHMARKS_DIR, LOCAL_BENCHMARKS_DIR)
                            val testSuite = serializer.decodeFromString<TestSuite>(
                                benchmarkDir.walk().firstOrNull { it.name == "testSuite.json" }?.readText()
                                    ?: return@async
                            ).remap(DOCKER_RESULTS_DIR, LOCAL_RESULTS_DIR)
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
                        }
                    }
                }
            }
        }
        allJobs.awaitAll()

        log.debug(allData.joinToString("\n"))
    }
//    val resultFiles = Files.walk(Paths.get(args[0])).filter { it.name.endsWith("results.json") }
//        .map { it.toAbsolutePath().toString() }.toList()
//    val data = resultFiles.associate {
//        (it.substringBeforeLast('/').substringBeforeLast('/')
//            .substringAfterLast('/') + "-" + it.substringBeforeLast('/').substringAfterLast("/")) to getJsonSerializer(
//            pretty = true
//        ).decodeFromString<List<ToolResults>>(File(it).readText())
//            .filter { it.coverage.coverage.singleOrNull() != null }
//    }
//
//    val benchmarks = data.values.flatMap { it.map { toolResults -> toolResults.benchmark } }.associateBy { it.buildId }
//    val metrics = MetricsProvider(Paths.get("metrics.json")).let { provider ->
//        benchmarks.keys.associateWith { benchmark -> provider.getMetrics(benchmarks[benchmark]!!.remap(FROM, TO)) }
//            .also {
//                provider.save()
//            }
//    }
//    log.debug(metrics.size)
//
//    for ((tool, results) in data) {
//        val resultsBranches = results
//            .filterNot { it.coverage.coverage.singleOrNull() == null }
//            .filter { it.coverage.coverage.first().instructions.covered > 0U }
//            .filter { it.coverage.coverage.first().branches.total > 0U }
//
//        log.debug(
//            String.format(
//                "$tool: Average coverage: %.2f%s %.2f%s based on %d benchmarks",
//                100.0 * results.sumOf { it.coverage.coverage.first().lines.ratio } / results.size,
//                "%",
//                100.0 * resultsBranches.sumOf { it.coverage.coverage.first().branches.ratio } / resultsBranches.size,
//                "%",
//                results.size
//            )
//        )
//
//        val filteredLines = results.filter { it.coverage.compilationRate.denominator > 0 }
//        val filteredBranches = resultsBranches.filter { it.coverage.coverage.first().branches.ratio > 0.0 }
//        log.debug(
//            String.format(
//                "$tool: Average filtered coverage: %.2f%s %.2f%s based on %d benchmarks",
//                100.0 * filteredLines.sumOf { it .coverage.coverage.first().lines.ratio } / filteredLines.size,
//                "%",
//                100.0 * filteredBranches.sumOf { it.coverage.coverage.first().branches.ratio } / filteredBranches.size,
//                "%",
//                filteredLines.size
//            )
//        )
//        log.debug("$tool: no coverage for ${results.size - filteredLines.size} benchmarks")
//    }
//
//    for ((tool, results) in data) {
//        log.debug("Distribution of $tool")
//        val distribution = mutableMapOf<ValueModel, Pair<Int, Int>>()
//        for (toolResult in results) {
//            val benchmarkMetrics = metrics[toolResult.benchmark.buildId] ?: continue
//            label@ for (methodCoverage in toolResult.coverage.coverage.first().methods) {
//                if (methodCoverage.methodId.name == "<clinit>") continue
//                for ((branch, covered) in (methodCoverage.branches as? ExtendedCoverageInfo<BranchId>)?.coverage
//                    ?: emptyMap()) {
//                    val methodMetrics = benchmarkMetrics.methods.firstOrNull { it.methodId == methodCoverage.methodId }
//                    if (methodMetrics == null) {
//                        log.error("${toolResult.benchmark.buildId}:${methodCoverage.methodId} not found")
//                        break@label
//                    }
//                    val valueModel = methodMetrics.branches[branch]
//                    if (valueModel == null) {
//                        log.error("${toolResult.benchmark.buildId}:${methodCoverage.methodId}:$branch not found")
//                        continue
//                    }
//
//                    val oldDist = distribution.getOrDefault(valueModel, 0 to 0)
//                    distribution[valueModel] = when {
//                        covered -> oldDist.copy(first = oldDist.first + 1)
//                        else -> oldDist.copy(second = oldDist.second + 1)
//                    }
//                }
//            }
//        }
//        log.debug(
//            distribution.toList()
//                .joinToString("\n") {
//                    String.format(
//                        "%s: %d / %d -> %.2f",
//                        it.first,
//                        it.second.first,
//                        it.second.second,
//                        it.second.first.toDouble() / (it.second.first + it.second.second)
//                    )
//                }
//        )
//    }
}
