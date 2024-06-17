package org.plan.research.tga.core.coverage

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
data class Fraction(val numerator: Int, val denominator: Int) {
    val ratio: Double = numerator.toDouble() / denominator.toDouble()
}

@Serializable
sealed class CoverageInfo<T : Id> {
    abstract val covered: UInt
    abstract val total: UInt

    @Required
    val ratio: Double
        get() = when {
            total != 0U -> covered.toDouble() / total.toDouble()
            else -> 0.0
        }
}

@Serializable
data class BasicCoverageInfo<T : Id>(
    override val covered: UInt,
    override val total: UInt,
) : CoverageInfo<T>()

@Serializable
data class ExtendedCoverageInfo<T : Id>(
    val coverage: Map<T, Boolean>
) : CoverageInfo<T>() {
    @Required
    override val covered: UInt = coverage.count { it.value }.toUInt()

    @Required
    override val total: UInt = coverage.size.toUInt()
}

@Serializable
sealed interface Id

@Serializable
data class LineId(val fileName: String, val lineNumber: UInt) : Id

@Serializable
data class InstructionId(val line: LineId, val number: UInt) : Id

@Serializable
data class BranchId(val line: LineId, val number: UInt) : Id

@Serializable
data class MethodId(val name: String, val descriptor: String) : Id

@Serializable
data class ClassId(val name: String) : Id

@Serializable
sealed interface CodeCoverageInfo {
    val instructions: CoverageInfo<InstructionId>
    val lines: CoverageInfo<LineId>
    val branches: CoverageInfo<BranchId>

    fun print(): String = "instructions - %.2f, lines - %.2f, branches - %.2f".format(
        instructions.ratio * 100.0,
        lines.ratio * 100.0,
        branches.ratio * 100.0
    )
}

@Serializable
data class MethodCoverageInfo(
    val methodId: MethodId,
    override val instructions: CoverageInfo<InstructionId>,
    override val lines: CoverageInfo<LineId>,
    override val branches: CoverageInfo<BranchId>
) : CodeCoverageInfo {
    override fun toString(): String = "Method $methodId coverage: ${print()}"
}

@Serializable
data class ClassCoverageInfo(
    val klassId: ClassId,
    val methods: Set<MethodCoverageInfo>
) : CodeCoverageInfo {
    @Required
    override val instructions = BasicCoverageInfo<InstructionId>(
        methods.sumOf { it.instructions.covered }, methods.sumOf { it.instructions.total }
    )

    @Required
    override val lines = BasicCoverageInfo<LineId>(
        methods.sumOf { it.lines.covered }, methods.sumOf { it.lines.total }
    )

    @Required
    override val branches = BasicCoverageInfo<BranchId>(
        methods.sumOf { it.branches.covered }, methods.sumOf { it.branches.total }
    )

    override fun toString(): String = "Class $klassId coverage: ${print()}"
}

@Serializable
data class TestSuiteCoverage(
    val compilationRate: Fraction,
    val coverage: Set<ClassCoverageInfo>
)
