package org.plan.research.tga.analysis

import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.tool.ToolResults
import org.vorpal.research.kthelper.logging.log
import java.io.File

fun main(args: Array<String>) {
    println("Hello World!")

    val results = getJsonSerializer(pretty = true).decodeFromString<List<ToolResults>>(File(args[0]).readText())

    log.debug("results: {}", results)

    log.debug(
        "Average coverage: {} {}",
        results.sumOf { it.coverage.lines.ratio } / results.size,
        results.sumOf { it.coverage.branches.ratio } / results.size
    )

    val filteredResults = results.filter { it.coverage.lines.ratio > 0.0 }
    log.debug(
        "Average filtered coverage: {} {}",
        filteredResults.sumOf { it.coverage.lines.ratio } / filteredResults.size,
        filteredResults.sumOf { it.coverage.branches.ratio } / filteredResults.size
    )
}
