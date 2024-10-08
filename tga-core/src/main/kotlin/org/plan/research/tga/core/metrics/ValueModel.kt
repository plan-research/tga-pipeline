package org.plan.research.tga.core.metrics

import kotlinx.serialization.Serializable
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

@Serializable
sealed class ValueModel(private val level: UInt) {
    open fun join(other: ValueModel): ValueModel = when {
        level < other.level -> other
        level > other.level -> this
        this == other -> this
        else -> unreachable { log.error("$this join $other = ???") }
    }
}

@Serializable
data object Top : ValueModel(UInt.MAX_VALUE)

@Serializable
data object Bottom : ValueModel(UInt.MIN_VALUE)

@Serializable
data object PrimitiveModel : ValueModel(10_000U)
@Serializable
data object NullModel : ValueModel(10_000U)

@Serializable
data object SwitchModel : ValueModel(11_000U)

@Serializable
data object TypeCheckModel : ValueModel(15_000U)

@Serializable
data object StaticModel : ValueModel(20_000U)

@Serializable
data object StringModel : ValueModel(30_000U)

@Serializable
data object CollectionModel : ValueModel(60_000U)
@Serializable
data object StdLibModel : ValueModel(70_000U)

@Serializable
data object MixedModel : ValueModel(100_000U)
