package org.plan.research.tga.analysis.metrics

import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.MethodId
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.HandleBsmArgument
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.graph.GraphTraversal

class CyclomaticComplexityCounter(
    override val cm: ClassManager
) : MethodVisitor {
    companion object {
        private val complexity = mutableMapOf<Pair<ClassId, MethodId>, UInt>()

        fun getComplexity(method: Pair<ClassId, MethodId>) = complexity.getOrDefault(method, UInt.MAX_VALUE)
        fun reset() {
            complexity.clear()
        }
    }

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (method.fullId in complexity) return
        complexity[method.fullId] = 1U

        var e = 0U
        val n = method.body.basicBlocks.size.toUInt()
        val p = GraphTraversal(method.body).components().first

        for (block in method.body) {
            e += (block.successors.size + block.handlers.size).toUInt()
            block.filterIsInstance<InvokeDynamicInst>().flatMap { it.bootstrapMethodArgs }.filterIsInstance<HandleBsmArgument>()
                .forEach { handle ->
                    visit(handle.handle.method)
                    e += getComplexity(handle.handle.method.fullId)
                }
        }

        complexity[method.fullId] = e - n + 2U * p
    }
}
