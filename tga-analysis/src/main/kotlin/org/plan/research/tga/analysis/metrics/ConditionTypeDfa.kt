package org.plan.research.tga.analysis.metrics

import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.LineId
import org.plan.research.tga.core.coverage.MethodId
import org.plan.research.tga.core.metrics.Bottom
import org.plan.research.tga.core.metrics.CollectionModel
import org.plan.research.tga.core.metrics.MixedModel
import org.plan.research.tga.core.metrics.NullModel
import org.plan.research.tga.core.metrics.PrimitiveModel
import org.plan.research.tga.core.metrics.StaticModel
import org.plan.research.tga.core.metrics.StdLibModel
import org.plan.research.tga.core.metrics.StringModel
import org.plan.research.tga.core.metrics.SwitchModel
import org.plan.research.tga.core.metrics.TypeCheckModel
import org.plan.research.tga.core.metrics.ValueModel
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.collectionClass
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CallOpcode
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.CmpOpcode
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.UnknownValueInst
import org.vorpal.research.kfg.ir.value.instruction.UnreachableInst
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.NullType
import org.vorpal.research.kfg.type.PrimitiveType
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.logging.log

private typealias ValueModelMap = MutableMap<Value, ValueModel>

private fun ValueModelMap(): ValueModelMap = mutableMapOf()

class ConditionTypeDfa(
    override val cm: ClassManager,
    private val projectPackage: Package
) : MethodVisitor {
    private val blockEntries = mutableMapOf<BasicBlock, ValueModelMap>()
    private val blockExits = mutableMapOf<BasicBlock, ValueModelMap>()
    private lateinit var valueDomains: ValueModelMap

    companion object {
        private val regexPackage = Package.parse("java.util.regex.*")
        private val stdlibPackage = Package.parse("java.*")
        private val stringTypes = setOf(
            SystemTypeNames.stringClass,
            SystemTypeNames.stringBuffer,
            SystemTypeNames.stringBuilder,
            SystemTypeNames.charSequence
        )
        private val staticCollectionCallTypes = setOf(
            "java/util/Collections",
        )
        private val iteratorCallTypes = setOf(
            "java/util/Iterator"
        )

        private val ids = mutableMapOf<Location, UInt>()
        private val methodMetrics = mutableMapOf<Pair<ClassId, MethodId>, MutableMap<BranchId, ValueModel>>()

        fun getMetrics(method: Pair<ClassId, MethodId>): Map<BranchId, ValueModel> =
            methodMetrics.getOrDefault(method, emptyMap())

        fun reset() {
            ids.clear()
            methodMetrics.clear()
        }
    }

    @Suppress("RecursivePropertyAccessor")
    val Type.isConcreteType: Boolean get() = when (this) {
        is ClassType -> klass is ConcreteClass
        is ArrayType -> component.isConcreteType
        else -> false
    }

    private fun convert(type: Type): ValueModel = when {
        type is PrimitiveType -> PrimitiveModel
//        type in types.primitiveWrapperTypes -> PrimitiveModel
        type is NullType -> NullModel
        type.name in stringTypes -> StringModel
        regexPackage.isParent(type.name) -> StringModel
        type.isConcreteType && type.isSubtypeOf(cm["java/util/Iterator"].asType) -> CollectionModel
        type.isConcreteType && type.isSubtypeOf(cm.collectionClass.asType) -> CollectionModel
        stdlibPackage.isParent(type.name) -> StdLibModel
        projectPackage.isParent(type.name) -> MixedModel
        else -> MixedModel
    }

    private fun convert(value: Value): ValueModel = valueDomains.getOrPut(value) {
        when (value) {
            is CallInst -> convertCall(value)
            else -> convert(value.type)
        }
    }

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (!method.hasBody) return
        if (method.fullId in methodMetrics) return
        val metrics = methodMetrics.getOrPut(method.fullId, ::mutableMapOf)
        log.debug("Analyzing method {}", method)

        val entryMap = ValueModelMap()
        valueDomains = entryMap
        for ((index, type) in method.argTypes.withIndex()) {
            val argValue = values.getArgument(index, method, type)
            entryMap[argValue] = convert(argValue)
        }
        blockEntries[method.body.entry] = entryMap

        val queue = queueOf(method.body.entry)
        val inQueue = mutableMapOf(method.body.entry to 1)
        val visited = mutableSetOf<BasicBlock>()
        while (queue.isNotEmpty()) {
            val currentBlock = queue.poll()!!
            inQueue[currentBlock] = inQueue[currentBlock]!! - 1
            if (inQueue[currentBlock]!! > 0) continue

            val predecessorExits = when (currentBlock) {
                is CatchBlock -> currentBlock.throwers
                else -> currentBlock.predecessors
            }.map { blockExits.getOrPut(it) { ValueModelMap() } }

            val currentEntry = blockEntries.getOrPut(currentBlock) { ValueModelMap() }
            for (key in predecessorExits.flatMapTo(mutableSetOf()) { it.keys }) {
                currentEntry[key] = predecessorExits.fold(Bottom as ValueModel) { a, b ->
                    a.join(b.getOrDefault(key, Bottom))
                }
            }

            val currentExit = blockExits.getOrPut(currentBlock) { ValueModelMap() }
            currentExit.clear()
            currentExit.putAll(currentEntry)
            valueDomains = currentExit

            visitBasicBlock(currentBlock)

            if (currentEntry == currentExit && currentBlock in visited) continue

            visited += currentBlock
            queue += currentBlock.successors
            queue += currentBlock.handlers
            currentBlock.successors.forEach {
                inQueue[it] = inQueue.getOrDefault(it, 0) + 1
            }
            currentBlock.handlers.forEach {
                inQueue[it] = inQueue.getOrDefault(it, 0) + 1
            }
        }

        for (block in method.body) {
            val terminator = block.terminator
            val location = terminator.location
            val lineId = LineId(location.file, location.line.toUInt())

            val terminatorValueType = blockExits[block]!![terminator] ?: continue
            for (successor in terminator.successors) {
                val branchId = ids.getOrDefault(location, 0U)
                metrics[BranchId(lineId, branchId)] = terminatorValueType
                ids[location] = branchId + 1U
            }
        }
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        valueDomains[inst] = valueDomains[inst.arrayRef]!!
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {}

    override fun visitBinaryInst(inst: BinaryInst) {
        valueDomains[inst] = convert(inst.lhv).join(convert(inst.rhv))
    }

    override fun visitBranchInst(inst: BranchInst) {
        valueDomains[inst] = convert(inst.cond)
    }

    private fun convertCall(inst: CallInst): ValueModel = when (inst.opcode) {
        CallOpcode.STATIC -> when {
            inst.method.returnType.name in iteratorCallTypes -> CollectionModel
            inst.method.klass.fullName in staticCollectionCallTypes -> CollectionModel
            inst.method.isNative -> MixedModel
            else -> StaticModel
        }

        else -> when {
            inst.method.returnType.name in iteratorCallTypes -> CollectionModel
            inst.method.klass.fullName in iteratorCallTypes -> CollectionModel
            inst.method.isNative -> MixedModel
            else -> convert(inst.callee)
        }
    }

    override fun visitCallInst(inst: CallInst) {
        valueDomains[inst] = convertCall(inst)
    }

    override fun visitCastInst(inst: CastInst) {
        valueDomains[inst] = convert(inst.operand)
    }

    override fun visitCatchInst(inst: CatchInst) {
        valueDomains[inst] = convert(inst)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val lhv = convert(inst.lhv)
        val rhv = convert(inst.rhv)

        valueDomains[inst] = when (inst.opcode) {
            CmpOpcode.EQ, CmpOpcode.NEQ -> if (lhv is NullModel || rhv is NullModel) NullModel else lhv.join(rhv)
            else -> lhv.join(rhv)
        }
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {}
    override fun visitExitMonitorInst(inst: ExitMonitorInst) {}

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        when {
            inst.hasOwner -> valueDomains[inst] = convert(inst.owner)
            else -> valueDomains[inst] = StaticModel
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {}

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        valueDomains[inst] = TypeCheckModel
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        valueDomains[inst] = MixedModel
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        valueDomains[inst] = convert(inst.type)
    }

    override fun visitNewInst(inst: NewInst) {
        valueDomains[inst] = convert(inst.type)
    }

    override fun visitPhiInst(inst: PhiInst) {
        valueDomains[inst] = inst.incomingValues.fold(Bottom as ValueModel) { a, b -> a.join(convert(b)) }
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        valueDomains[inst] = when (inst.opcode) {
            UnaryOpcode.NEG -> convert(inst.operand)
            UnaryOpcode.LENGTH -> PrimitiveModel
        }
    }

    override fun visitJumpInst(inst: JumpInst) {}
    override fun visitReturnInst(inst: ReturnInst) {}
    override fun visitSwitchInst(inst: SwitchInst) {
        valueDomains[inst] = SwitchModel
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        valueDomains[inst] = SwitchModel
    }

    override fun visitThrowInst(inst: ThrowInst) {}
    override fun visitUnreachableInst(inst: UnreachableInst) {}
    override fun visitUnknownValueInst(inst: UnknownValueInst) {}
}
