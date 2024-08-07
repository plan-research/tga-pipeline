package org.plan.research.tga.analysis.coverage.jacoco

import kotlinx.serialization.encodeToString
import org.apache.commons.cli.Option
import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.tool.TestSuite
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter
import kotlin.io.path.readText


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
                Option("c", "compilationResult", true, "path to compilation result file in JSON format").also {
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

    val coverageProvider = JacocoCliCoverageProvider()

    val serializer = getJsonSerializer(pretty = false)

    val benchmark = serializer.decodeFromString<Benchmark>(Paths.get(config.getCmdValue("benchmark")!!).readText())
    val testSuite = serializer.decodeFromString<TestSuite>(Paths.get(config.getCmdValue("testSuite")!!).readText())
    val compilationResult = serializer.decodeFromString<CompilationResult>(Paths.get(config.getCmdValue("compilationResult")!!).readText())
    val coverage = coverageProvider.computeCoverage(benchmark, testSuite, compilationResult)

    val outputFile = Paths.get(config.getCmdValue("output")!!)
    outputFile.parent.toFile().mkdirs()
    outputFile.bufferedWriter().use {
        it.write(serializer.encodeToString(coverage))
    }
}
