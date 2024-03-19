package org.plan.research.tga.runner

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.runner.config.TgaRunnerConfig
import java.nio.file.Paths
import kotlin.io.path.writeText
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main(args: Array<String>) {
    val config = TgaRunnerConfig(args)

    val port = config.getCmdValue("port")!!.toUInt()
    val benchmarks = Paths.get(config.getCmdValue("config")!!)
    val timeLimit = config.getCmdValue("timeout")!!.toInt().toDuration(DurationUnit.SECONDS)
    val outputDirectory = Paths.get(config.getCmdValue("output")!!)

    val n = config.getCmdValue("runs")!!.toInt()
    val name = config.getCmdValue("name")!!

    val runner = TgaRunner(port, benchmarks, timeLimit, outputDirectory, n, name)
    val results = runner.run()

    val resultFile = outputDirectory.resolve("results.json")
    resultFile.writeText(getJsonSerializer(pretty = true).encodeToString(results))
}
