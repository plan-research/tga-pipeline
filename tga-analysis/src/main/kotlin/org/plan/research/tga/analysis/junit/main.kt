package org.plan.research.tga.analysis.junit

import kotlinx.serialization.encodeToString
import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter
import kotlin.io.path.readText
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val serializer = getJsonSerializer(pretty = true)
    val compilationResult = serializer.decodeFromString<CompilationResult>(Paths.get(args[0]).readText())
    val testName = args[1]
    val runner = JUnitRunner()
    try {
        val stackTraces = runner.run(compilationResult, testName)
        compilationResult.compiledDir.resolve("stackTrace.json").bufferedWriter().use {
            it.write(serializer.encodeToString(stackTraces))
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        exitProcess(1)
    }
}
