package org.plan.research.tga.runner

import org.plan.research.tga.runner.config.TgaRunnerConfig
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main(args: Array<String>) {
    val config = TgaRunnerConfig(args)

    val port = config.getCmdValue("port")!!.toUInt()
    val benchmarks = Paths.get(config.getCmdValue("config")!!)
    val timeLimit = config.getCmdValue("timeout")!!.toInt().toDuration(DurationUnit.SECONDS)
    val outputDirectory = Paths.get(config.getCmdValue("output")!!)

    val baseRunName = config.getCmdValue("runName", "run")
    val runIds = try {
        config.getCmdValue("runs")!!.let { str ->
            when {
                ".." in str -> {
                    val nums = str.split("..")
                    IntRange(nums[0].toInt(), nums[1].toInt())
                }

                else -> IntRange(str.toInt(), str.toInt())
            }
        }
    } catch (e: NumberFormatException) {
        log.error("Could not parse run command line argument, ", e)
        config.printHelp()
        exitProcess(1)
    }
    if (runIds.isEmpty()) {
        log.error("Run ids interval cannot be empty")
        config.printHelp()
        exitProcess(1)
    }

    val runner = TgaRunner(port, benchmarks, timeLimit, outputDirectory, baseRunName, runIds)
    runner.run()
}
