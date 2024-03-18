package org.plan.research.tga.core.coverage

data class CoverageInfo<T : Any>(
    val coverage: Map<T, Boolean>
) {
    val covered: UInt = coverage.count { it.value }.toUInt()
    val total: UInt = coverage.size.toUInt()
    val ratio = covered / total
}

data class LineId(val fileName: String, val lineNumber: UInt)
data class InstructionId(val line: LineId, val number: UInt)
data class BranchId(val line: LineId, val number: UInt)

interface CodeCoverageInfo {
    val instructions: CoverageInfo<InstructionId>
    val lines: CoverageInfo<LineId>
    val branches: CoverageInfo<BranchId>
}

data class MethodCoverageInfo(
    val name: String,
    val descriptor: String,
    override val instructions: CoverageInfo<InstructionId>,
    override val lines: CoverageInfo<LineId>,
    override val branches: CoverageInfo<BranchId>
) : CodeCoverageInfo

data class ClassCoverageInfo(
    val name: String,
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
