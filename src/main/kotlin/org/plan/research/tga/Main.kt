package org.plan.research.tga

import org.plan.research.tga.benchmark.json.JsonBenchmarkProvider
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Paths

fun main() {
    val provider = JsonBenchmarkProvider(Paths.get("test.json"))
    for (benchmark in provider.benchmarks()) {
        log.debug(benchmark)
    }
}
