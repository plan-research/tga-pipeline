package org.plan.research.tga.runner.metrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.MethodId
import org.plan.research.tga.runner.coverage.jacoco.asmString
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.executeClassPipeline
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

val Class.id get() = ClassId(this.fullName)
val Method.id get() = MethodId(this.name, this.asmDesc)
val Method.fullId: Pair<ClassId, MethodId> get() = klass.id to this.id

@Serializable
data class ClassMetrics(
    val klassId: ClassId,
    val methods: Set<MethodMetrics>
)

@Serializable
data class MethodMetrics(
    val methodId: MethodId,
    val complexity: UInt,
    val branches: Map<BranchId, ValueModel>
)


class MetricsProvider(private val metricsFile: Path) {
    private val metrics = mutableMapOf<String, ClassMetrics>()

    init {
        if (metricsFile.exists()) {
            val precomputedMetrics = getJsonSerializer(pretty = true)
                .decodeFromString<Set<ClassMetrics>>(metricsFile.readText())
            for (metric in precomputedMetrics) {
                metrics[metric.klassId.name] = metric
            }
        }
    }

    fun getMetrics(benchmark: Benchmark) = metrics.getOrPut(benchmark.klass) { computeMetrics(benchmark) }

    fun save() {
        metricsFile.writeText(
            getJsonSerializer(pretty = true).encodeToString(metrics.values.toList())
        )
    }
}

private fun computeMetrics(benchmark: Benchmark): ClassMetrics {
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
        it.asContainer()
    })

    val target = classManager[benchmark.klass.asmString]
    executeClassPipeline(classManager, target) {
        +CyclomaticComplexityCounter(classManager)
        +ConditionTypeDfa(classManager, target.pkg)
    }

    return ClassMetrics(ClassId(benchmark.klass), target.allMethods.mapTo(mutableSetOf()) {
        MethodMetrics(
            it.id,
            CyclomaticComplexityCounter.getComplexity(it.fullId),
            ConditionTypeDfa.getMetrics(it.fullId)
        )
    })
}
