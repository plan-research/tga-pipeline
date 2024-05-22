package org.plan.research.tga.analysis

import org.plan.research.tga.analysis.metrics.MetricsProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.metrics.ValueModel
import org.plan.research.tga.core.tool.ToolResults
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import java.io.File
import java.nio.file.Paths

val FROM = Paths.get("/var/benchmarks/gitbug")
val TO = Paths.get("/home/abdullin/workspace/tga-pipeline/benchmarks")


fun main(args: Array<String>) {
    val data = args.associate {
        it.substringBeforeLast('/').substringBeforeLast('/')
            .substringAfterLast('/') to getJsonSerializer(pretty = true).decodeFromString<List<ToolResults>>(File(it).readText())
    }

    val benchmarks = data.values.flatMap { it.map { toolResults -> toolResults.benchmark } }.associateBy { it.buildId }
    val metrics = MetricsProvider(Paths.get("metrics.json")).let { provider ->
        benchmarks.keys.associateWith { benchmark -> provider.getMetrics(benchmarks[benchmark]!!.remap(FROM, TO)) }
            .also {
                provider.save()
            }
    }
    log.debug(metrics.size)

    for ((tool, results) in data) {
        val resultsBranches = results.filter { it.coverage.branches.total > 0U }

        log.debug(
            "$tool: Average coverage: {} {}",
            results.sumOf { it.coverage.lines.ratio } / results.size,
            resultsBranches.sumOf { it.coverage.branches.ratio } / resultsBranches.size
        )

        val filteredLines = results.filter { it.coverage.lines.ratio > 0.0 }
        val filteredBranches = resultsBranches.filter { it.coverage.branches.ratio > 0.0 }
        log.debug(
            "$tool: Average filtered coverage: {} {}",
            filteredLines.sumOf { it.coverage.lines.ratio } / filteredLines.size,
            filteredBranches.sumOf { it.coverage.branches.ratio } / filteredBranches.size
        )
    }

    for ((tool, results) in data) {
        log.debug("Distribution of $tool")
        var notFoundMethod = 0
        var notFoundBranch = 0
        val distribution = mutableMapOf<ValueModel, Pair<Int, Int>>()
        for (toolResult in results) {
            val benchmarkMetrics = metrics[toolResult.benchmark.buildId] ?: continue
            for (methodCoverage in toolResult.coverage.methods) {
                if (methodCoverage.methodId.name == "<clinit>") continue
                for ((branch, covered) in methodCoverage.branches.coverage) {
                    val methodMetrics = benchmarkMetrics.methods.firstOrNull { it.methodId == methodCoverage.methodId }
                    if (methodMetrics == null) {
                        notFoundMethod++
                        continue
                    }
                    val valueModel = methodMetrics.branches[branch]
                    if (valueModel == null) {
                        notFoundBranch++
                        continue
                    }

                    val oldDist = distribution.getOrDefault(valueModel, 0 to 0)
                    distribution[valueModel] = when {
                        covered -> oldDist.copy(first = oldDist.first + 1)
                        else -> oldDist.copy(second = oldDist.second + 1)
                    }
                }
            }
        }
        log.debug("Not found methods $notFoundMethod, not found branches $notFoundBranch")
        log.debug(distribution)
    }
}
