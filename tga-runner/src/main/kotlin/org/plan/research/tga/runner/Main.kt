package org.plan.research.tga.runner

import org.plan.research.tga.runner.config.TgaRunnerConfig
import java.nio.file.Paths
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main(args: Array<String>) {
    val config = TgaRunnerConfig(args)

    val port = config.getCmdValue("port")!!.toUInt()
    val benchmarks = Paths.get(config.getCmdValue("config")!!)
    val timeLimit = config.getCmdValue("timeout")!!.toInt().toDuration(DurationUnit.SECONDS)
    val outputDirectory = Paths.get(config.getCmdValue("output")!!)

    val n = config.getCmdValue("runs")!!.toInt()

    val runner = TgaRunner(port, benchmarks, timeLimit, outputDirectory, n)
    runner.run()
}
