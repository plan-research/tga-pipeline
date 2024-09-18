@file:Suppress("UNUSED_VARIABLE", "UnusedImport")

package org.plan.research.tga.analysis

import org.plan.research.tga.analysis.junit.StackTrace
import org.plan.research.tga.analysis.metrics.MetricsProvider
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.metrics.CollectionModel
import org.plan.research.tga.core.metrics.IteratorModel
import org.plan.research.tga.core.metrics.LambdaModel
import org.plan.research.tga.core.metrics.MixedModel
import org.plan.research.tga.core.metrics.NativeModel
import org.plan.research.tga.core.metrics.NullModel
import org.plan.research.tga.core.metrics.PrimitiveModel
import org.plan.research.tga.core.metrics.ProjectModel
import org.plan.research.tga.core.metrics.RegexModel
import org.plan.research.tga.core.metrics.StaticModel
import org.plan.research.tga.core.metrics.StdLibModel
import org.plan.research.tga.core.metrics.StringModel
import org.plan.research.tga.core.metrics.SwitchModel
import org.plan.research.tga.core.metrics.TypeCheckModel
import org.vorpal.research.kthelper.resolve
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val input = Paths.get(args[0])
    val benchmarks =
        JsonBenchmarkProvider(Paths.get("benchmarks/benchmarks.json")).benchmarks().associateBy { it.buildId }
    val benchmarkProperties = tryOrNull {
        getJsonSerializer(pretty = true)
            .decodeFromString<List<Properties>>(Paths.get("properties.json").readText())
            .associate { it.benchmark to it.properties }
    } ?: emptyMap()
    val metricsProvider = MetricsProvider(Paths.get("metrics.json"), Paths.get("cyclomatic-complexity.txt"))

    val header = "tool,runName,iteration,benchmark buildId,benchmark klass," +
            "compiled tests,total tests,compilation rate," +
            "covered lines,total lines,line coverage," +
            "covered branches,total branches,branch coverage," +
            "killed mutants,total mutants,mutation score," +
            "package,internal dependencies,stdlib dependencies,external dependencies," +
            "language,comments,java docs,sloc,failure reproduction,cyclomatic complexity"
    val valueTypes = listOf(
        "PrimitiveModel" to PrimitiveModel,
        "NullModel" to NullModel,
        "SwitchModel" to SwitchModel,
        "TypeCheckModel" to TypeCheckModel,
        "StaticModel" to StaticModel,
        "StringModel" to StringModel,
        "RegexModel" to RegexModel,
        "LambdaModel" to LambdaModel,
        "IteratorModel" to IteratorModel,
        "CollectionModel" to CollectionModel,
        "StdLibModel" to StdLibModel,
        "NativeModel" to NativeModel,
        "ProjectModel" to ProjectModel,
        "MixedModel" to MixedModel,
    )

    val fullHeader = header + "," + valueTypes.joinToString(separator = ",") { it.first }

    val newCsv = Paths.get("EvoSuite-metrics-version120-default.csv").bufferedWriter().use { writer ->
        writer.write(fullHeader)
        writer.write("\n")

        for (file in input.listDirectoryEntries().filter { it.name.endsWith(".csv") }) {
//            val (tool, runName, iteration) = file.name.removeSuffix(".csv").split('-')
            if (file.name.split('-').size != 4) continue
            val (tool, _, runName, iteration) = file.name.removeSuffix(".csv").split('-')
            if (runName != "version120") continue

            for (run in file.readLines()) {
                val fixedLine = run.split(", ").take(17).toMutableList()
                if (fixedLine[3] !in benchmarkProperties) {
                    continue
                }
                fixedLine.addAll(benchmarkProperties[fixedLine[3]]!!.toList().map { it.second })

                if (fixedLine[3] !in benchmarks) {
                    continue
                }

                for (index in listOf(7, 10, 13, 16)) {
                    if (fixedLine[index] == "NaN") {
                        fixedLine[index] = "0.00"
                    }
                }

                val benchmarkDir = input.resolve("$runName-$iteration", fixedLine[3])
                val prePatchFailures = tryOrNull {
                    getJsonSerializer(pretty = true)
                        .decodeFromString<Set<StackTrace>>(benchmarkDir.resolve("evosuite-tests", "failures.json").readText())
                } ?: emptySet()
                val patchFailures = tryOrNull {
                    getJsonSerializer(pretty = true)
                        .decodeFromString<Set<StackTrace>>(benchmarkDir.resolve("evosuite-tests", "failures-patched.json").readText())
                } ?: emptySet()

                val diff = (patchFailures - prePatchFailures) + (prePatchFailures - patchFailures)
                fixedLine.add(when {
                    diff.any { "AssertionError" in it.throwable } -> "100.00"
//                    diff.isNotEmpty() -> "50.00"
                    else -> "0.00"
                })


                val metrics = metricsProvider.getMetrics(benchmarks[fixedLine[3]]!!)
                fixedLine.add(metrics.complexity.toString())

                val branches = metrics.methods.flatMap { it.branches.toList() }.groupBy({ it.second }, { it.first })
                for (value in valueTypes) {
                    fixedLine.add(branches[value.second]?.size?.toString() ?: "0")
                }

                writer.write(fixedLine.joinToString(separator = ","))
                writer.write("\n")
            }
        }
    }
    metricsProvider.save()
}
