package org.plan.research.tga.core.util

import org.slf4j.MDC
import org.vorpal.research.kfg.Package
import java.lang.reflect.Array
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.relativeToOrSelf

val TGA_PIPELINE_HOME: Path = Paths.get(System.getProperty("tga.pipeline.home"))


fun initLog(outputDirectory: Path, filename: String) {
    MDC.put("kex-run-id", outputDirectory.resolve(filename).toAbsolutePath().toString())
}

val String.asmString get() = replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)

fun Class<*>.asArray(): Class<*> = Array.newInstance(this, 0).javaClass

fun Path.remap(from: Path, to: Path): Path = to.resolve(this.relativeToOrSelf(from))

val Boolean?.orFalse: Boolean get() = this ?: false
val Boolean?.orTrue: Boolean get() = this ?: true
