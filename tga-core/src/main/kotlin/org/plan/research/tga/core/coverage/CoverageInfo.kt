package org.plan.research.tga.core.coverage

import kotlinx.serialization.Serializable

@Serializable
data class CoverageInfo<T : Any>(
    val coverage: Map<T, Boolean>
) {
    val covered: UInt = coverage.count { it.value }.toUInt()
    val total: UInt = coverage.size.toUInt()
    val ratio = covered / total
}

@Serializable
data class LineId(val fileName: String, val lineNumber: UInt)
@Serializable
data class InstructionId(val line: LineId, val number: UInt)
@Serializable
data class BranchId(val line: LineId, val number: UInt)

@Serializable
data class MethodId(val name: String, val descriptor: String)
@Serializable
data class ClassId(val name: String)

interface CodeCoverageInfo {
    val instructions: CoverageInfo<InstructionId>
    val lines: CoverageInfo<LineId>
    val branches: CoverageInfo<BranchId>
}

@Serializable
data class MethodCoverageInfo(
    val methodId: MethodId,
    override val instructions: CoverageInfo<InstructionId>,
    override val lines: CoverageInfo<LineId>,
    override val branches: CoverageInfo<BranchId>
) : CodeCoverageInfo

@Serializable
data class ClassCoverageInfo(
    val klassId: ClassId,
    val methods: Set<MethodCoverageInfo>
) : CodeCoverageInfo {
    override val instructions = CoverageInfo<InstructionId>(methods.fold(mutableMapOf()) { a, b ->
        a.putAll(b.instructions.coverage)
        a
    })
    override val lines = CoverageInfo<LineId>(methods.fold(mutableMapOf()) { a, b ->
        a.putAll(b.lines.coverage)
        a
    })
    override val branches = CoverageInfo<BranchId>(methods.fold(mutableMapOf()) { a, b ->
        a.putAll(b.branches.coverage)
        a
    })
}
