package org.plan.research.tga.runner.metrics

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.MethodId
import org.plan.research.tga.runner.coverage.jacoco.asmString
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.visitor.executeClassPipeline

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

fun computeMetrics(benchmark: Benchmark): ClassMetrics {
    val classManager = ClassManager(
        KfgConfig(
            Flags.readAll,
            useCachingLoopManager = false,
            failOnError = false,
            verifyIR = false,
            checkClasses = false
        )
    )

    classManager.initialize(benchmark.classPath.map { it.asContainer()!! })

    val target = classManager[benchmark.klass.asmString]
    executeClassPipeline(classManager, target) {
        +CyclomaticComplexityCounter(classManager)
        +ConditionTypeDfa(classManager, target.pkg)
    }

    return ClassMetrics(ClassId(benchmark.klass), target.allMethods.mapTo(mutableSetOf()) {
        MethodMetrics(
            MethodId(it.name, it.asmDesc),
            CyclomaticComplexityCounter.getComplexity(it),
            ConditionTypeDfa.getMetrics(it)
        )
    })
}
