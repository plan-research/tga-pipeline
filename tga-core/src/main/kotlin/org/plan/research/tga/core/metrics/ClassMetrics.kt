package org.plan.research.tga.core.metrics

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.MethodId

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
