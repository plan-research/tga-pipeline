package org.plan.research.tga.core.util

import org.vorpal.research.kfg.Package
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.lang.reflect.Array
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readLines
import kotlin.io.path.relativeToOrSelf

val TGA_PIPELINE_HOME: Path = Paths.get(System.getenv("TGA_PIPELINE_HOME"))

val String.asmString get() = replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
val String.javaString get() = replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR).replace('$', '.')

fun Class<*>.asArray(): Class<*> = Array.newInstance(this, 0).javaClass

fun Path.remap(from: Path, to: Path): Path = to.resolve(this.relativeToOrSelf(from)).normalize()

val Boolean?.orFalse: Boolean get() = this ?: false
val Boolean?.orTrue: Boolean get() = this ?: true


fun getJvmVersion(): Int {
    val versionStr = System.getProperty("java.version")
    return """(1.)?(\d+)""".toRegex().find(versionStr)?.let {
        it.groupValues[2].toInt()
    } ?: unreachable { log.error("Could not detect JVM version: \"{}\"", versionStr) }
}

fun getJvmModuleParams(): List<String> = when (getJvmVersion()) {
    in 1..7 -> unreachable { log.error("Unsupported version of JVM: ${getJvmVersion()}") }
    8 -> emptyList()
    else -> buildList {
        val modules = TGA_PIPELINE_HOME.resolve("modules.info").readLines()
        for (module in modules) {
            add("--add-opens")
            add(module)
        }
        add("--illegal-access=warn")
    }
}
