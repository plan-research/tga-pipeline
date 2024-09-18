package org.plan.research.tga.analysis.metrics

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.JsonBenchmarkProvider
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.MethodId
import org.plan.research.tga.core.metrics.ClassMetrics
import org.plan.research.tga.core.metrics.MethodMetrics
import org.plan.research.tga.core.util.asmString
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.executeClassPipeline
import org.vorpal.research.kthelper.collection.mapTo
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

val Class.id get() = ClassId(this.fullName)
val Method.id get() = MethodId(this.name, this.asmDesc)
val Method.fullId: Pair<ClassId, MethodId> get() = klass.id to this.id

class MetricsProvider(
    private val metricsFile: Path,
    private val complexityFile: Path,
) {
    private val metrics = mutableMapOf<Pair<String, ClassId>, ClassMetrics>()
    private val complexities = mutableMapOf<String, UInt>()

    init {
        if (metricsFile.exists()) {
            val precomputedMetrics = getJsonSerializer(pretty = true)
                .decodeFromString<List<Pair<Pair<String, ClassId>, ClassMetrics>>>(metricsFile.readText())
            for (metric in precomputedMetrics) {
                metrics[metric.first] = metric.second
            }
        }
        if (complexityFile.exists()) {
            complexities.putAll(complexityFile.readLines().filter { it.isNotBlank() }.mapTo(mutableMapOf()) { line ->
                line.split(" ").let { it.first() to it.last().toUInt() }
            })
        }
    }

    fun getMetrics(benchmark: Benchmark) =
        metrics.getOrPut(benchmark.buildId to ClassId(benchmark.klass)) { computeMetrics(benchmark) }

    fun save() {
        metricsFile.writeText(
            getJsonSerializer(pretty = true).encodeToString(metrics.toList())
        )
    }

    private fun computeMetrics(benchmark: Benchmark): ClassMetrics {
        // TODO: refactor this
        // currently we need to manually clear the visitor's global state after each run,
        // because some of the benchmarks may contain different versions of the same classes
        ConditionTypeDfa.reset()
        CyclomaticComplexityCounter.reset()

        val classManager = ClassManager(
            KfgConfig(
                Flags.readAll,
                useCachingLoopManager = false,
                failOnError = false,
                verifyIR = false,
                checkClasses = false
            )
        )

        classManager.initialize(benchmark.classPath.mapNotNull {
            tryOrNull { it.asContainer() }
        })

        val target = classManager[benchmark.klass.asmString]
        executeClassPipeline(classManager, target) {
            +CyclomaticComplexityCounter(classManager)
            +ConditionTypeDfa(classManager, target.pkg)
        }

        return ClassMetrics(
            ClassId(benchmark.klass),
            complexity = complexities[benchmark.buildId] ?: 0U,
            target.allMethods.mapTo(mutableSetOf()) {
                MethodMetrics(
                    it.id,
                    ConditionTypeDfa.getMetrics(it.fullId)
                )
            })
    }
}


fun main(args: Array<String>) {
    val benchmarks = JsonBenchmarkProvider(Paths.get(args[0]))
    val metricsProvider = MetricsProvider(
        "metrics.json".let { Path.of(it) },
        "cyclomatic-complexity.txt".let { Path.of(it) }
    )
    for (benchmark in benchmarks.benchmarks()) {
        val metrics = metricsProvider.getMetrics(benchmark)
        println(metrics)
    }

    metricsProvider.save()
}
