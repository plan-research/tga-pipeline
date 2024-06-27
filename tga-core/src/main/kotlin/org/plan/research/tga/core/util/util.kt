package org.plan.research.tga.core.util

import org.vorpal.research.kfg.Package
import java.lang.reflect.Array
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.relativeToOrSelf

val TGA_PIPELINE_HOME: Path = Paths.get(System.getenv("TGA_PIPELINE_HOME"))

val String.asmString get() = replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
val String.javaString get() = replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

fun Class<*>.asArray(): Class<*> = Array.newInstance(this, 0).javaClass

fun Path.remap(from: Path, to: Path): Path = to.resolve(this.relativeToOrSelf(from))

val Boolean?.orFalse: Boolean get() = this ?: false
val Boolean?.orTrue: Boolean get() = this ?: true
