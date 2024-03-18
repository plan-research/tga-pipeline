package org.plan.research.tga.runner

import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.runner.config.TgaConfig
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Paths

fun main(args: Array<String>) {
    val config = TgaConfig(args)

    val benchmarksConfig = config.getCmdValue("config")!!
    val provider = JsonBenchmarkProvider(Paths.get(benchmarksConfig))
    for (benchmark in provider.benchmarks()) {
//        log.debug(benchmark)
        for (dependency in benchmark.classPath) {
            if ("junit" in dependency.toString()) {
                log.debug(dependency)
            }
        }
    }
}
