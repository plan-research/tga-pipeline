package org.plan.research.tga.runner.metrics

import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.MethodId
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.graph.GraphTraversal

class CyclomaticComplexityCounter(
    override val cm: ClassManager
) : MethodVisitor {
    companion object {
        private val complexity = mutableMapOf<Pair<ClassId, MethodId>, UInt>()

        fun getComplexity(method: Pair<ClassId, MethodId>) = complexity.getOrDefault(method, UInt.MAX_VALUE)
    }

    override fun cleanup() {}

    override fun visit(method: Method) {
        var e = 0U
        val n = method.body.basicBlocks.size.toUInt()
        val p = GraphTraversal(method.body).components().first

        for (block in method.body) {
            e += (block.successors.size + block.handlers.size).toUInt()
        }

        complexity[method.fullId] = e - n + 2U * p
    }
}
