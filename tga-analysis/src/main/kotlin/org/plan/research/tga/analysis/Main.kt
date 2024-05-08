package org.plan.research.tga.analysis

import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.tool.ToolResults
import org.vorpal.research.kthelper.logging.log
import java.io.File

fun main(args: Array<String>) {
    val results = getJsonSerializer(pretty = true).decodeFromString<List<ToolResults>>(File(args[0]).readText())
    val resultsBranches = results.filter { it.coverage.branches.total > 0U }

    log.debug(
        "Average coverage: {} {}",
        results.sumOf { it.coverage.lines.ratio } / results.size,
        resultsBranches.sumOf { it.coverage.branches.ratio } / resultsBranches.size
    )

    val filteredLines = results.filter { it.coverage.lines.ratio > 0.0 }
    val filteredBranches = resultsBranches.filter { it.coverage.branches.ratio > 0.0 }
    log.debug(
        "Average filtered coverage: {} {}",
        filteredLines.sumOf { it.coverage.lines.ratio } / filteredLines.size,
        filteredBranches.sumOf { it.coverage.branches.ratio } / filteredBranches.size
    )
}
