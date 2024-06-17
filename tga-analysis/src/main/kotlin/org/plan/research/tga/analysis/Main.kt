package org.plan.research.tga.analysis

import org.plan.research.tga.analysis.metrics.MetricsProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ExtendedCoverageInfo
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
        val resultsBranches = results
            .filterNot { it.coverage.coverage.singleOrNull() == null }
            .filter { it.coverage.coverage.first().instructions.covered > 0U }
            .filter { it.coverage.coverage.first().branches.total > 0U }

        log.debug(
            String.format(
                "$tool: Average coverage: %.2f%s %.2f%s based on %d benchmarks",
                100.0 * results.sumOf { it.coverage.coverage.first().lines.ratio } / results.size, "%",
                100.0 * resultsBranches.sumOf { it.coverage.coverage.first().branches.ratio } / resultsBranches.size, "%",
                results.size
            )
        )

        val filteredLines = results.filter { it.coverage.coverage.first().lines.ratio > 0.0 }
        val filteredBranches = resultsBranches.filter { it.coverage.coverage.first().branches.ratio > 0.0 }
        log.debug(
            String.format(
                "$tool: Average filtered coverage: %.2f%s %.2f%s based on %d benchmarks",
                100.0 * filteredLines.sumOf { it.coverage.coverage.first().lines.ratio } / filteredLines.size, "%",
                100.0 * filteredBranches.sumOf { it.coverage.coverage.first().branches.ratio } / filteredBranches.size, "%",
                filteredLines.size
            )
        )
        log.debug("$tool: no coverage for ${results.size - filteredLines.size} benchmarks")
    }

    for ((tool, results) in data) {
        log.debug("Distribution of $tool")
        val distribution = mutableMapOf<ValueModel, Pair<Int, Int>>()
        for (toolResult in results) {
            val benchmarkMetrics = metrics[toolResult.benchmark.buildId] ?: continue
            label@ for (methodCoverage in toolResult.coverage.coverage.first().methods) {
                if (methodCoverage.methodId.name == "<clinit>") continue
                for ((branch, covered) in (methodCoverage.branches as? ExtendedCoverageInfo<BranchId>)?.coverage ?: emptyMap()) {
                    val methodMetrics = benchmarkMetrics.methods.firstOrNull { it.methodId == methodCoverage.methodId }
                    if (methodMetrics == null) {
                        log.error("${toolResult.benchmark.buildId}:${methodCoverage.methodId} not found")
                        break@label
                    }
                    val valueModel = methodMetrics.branches[branch]
                    if (valueModel == null) {
                        log.error("${toolResult.benchmark.buildId}:${methodCoverage.methodId}:$branch not found")
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
        log.debug(
            distribution.toList()
                .joinToString("\n") {
                    String.format(
                        "%s: %d / %d -> %.2f",
                        it.first,
                        it.second.first,
                        it.second.second,
                        it.second.first.toDouble() / (it.second.first + it.second.second)
                    )
                }
        )
    }
}
