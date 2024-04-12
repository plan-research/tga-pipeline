package org.plan.research.tga.runner.coverage.jacoco

import kotlinx.serialization.encodeToString
import org.apache.commons.cli.Option
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.dependency.DependencyManager
import org.plan.research.tga.core.tool.TestSuite
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText


private class TgaCoverageConfig(args: Array<String>) : TgaConfig("tga-coverage", options, args) {
    companion object {
        val options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option("b", "benchmark", true, "path to benchmark file in JSON format").also {
                    it.isRequired = true
                }
            )

            addOption(
                Option("t", "testSuite", true, "path to test suite file in JSON format").also {
                    it.isRequired = true
                }
            )

            addOption(
                Option("o", "output", true, "output file").also {
                    it.isRequired = true
                }
            )
        }
    }
}


fun main(args: Array<String>) {
    val config = TgaCoverageConfig(args)

    val dependencyManager = DependencyManager()
    val coverageProvider = JacocoCoverageProvider(dependencyManager)

    val serializer = getJsonSerializer(pretty = false)

    val benchmark = serializer.decodeFromString<Benchmark>(Paths.get(config.getCmdValue("benchmark")!!).readText())
    val testSuite = serializer.decodeFromString<TestSuite>(Paths.get(config.getCmdValue("testSuite")!!).readText())
    val coverage = coverageProvider.computeCoverage(benchmark, testSuite)

    val outputFile = Paths.get(config.getCmdValue("output")!!)
    outputFile.parent.toFile().mkdirs()
    outputFile.writeText(serializer.encodeToString(coverage))
}
