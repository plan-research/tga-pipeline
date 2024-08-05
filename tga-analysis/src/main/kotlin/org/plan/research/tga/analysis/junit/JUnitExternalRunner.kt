package org.plan.research.tga.analysis.junit

import kotlinx.serialization.encodeToString
import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.plan.research.tga.core.util.getJvmModuleParams
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.resolve
import org.vorpal.research.kthelper.terminateOrKill
import org.vorpal.research.kthelper.tryOrNull
import java.util.concurrent.TimeUnit
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JUnitExternalRunner {
    companion object {
        private val JUNIT_RUNNER_JAR = TGA_PIPELINE_HOME.resolve("tga-analysis", "build", "libs", "tga-junit.jar")
    }

    fun run(compilationResult: CompilationResult): Set<StackTrace> = buildSet {
        val serializer = getJsonSerializer(pretty = true)
        val compilationResultFile = compilationResult.compiledDir.resolve("compilationResult.json")
        compilationResultFile.bufferedWriter().use {
            it.write(serializer.encodeToString(compilationResult))
        }
        for (testName in compilationResult.compilableTests.keys) {
            val process = buildProcess(
                "java",
                *getJvmModuleParams().toTypedArray(),
                "-jar",
                JUNIT_RUNNER_JAR.toString(),
                compilationResultFile.toString(),
                testName
            ) {
                this.inheritIO()
                this.environment()["TGA_PIPELINE_HOME"] = TGA_PIPELINE_HOME.toString()
            }
            process.waitFor(100.seconds.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            process.terminateOrKill(10U, waitTime = 500.milliseconds)

            val stackTraceFile = compilationResult.compiledDir.resolve("stackTrace.json")
            if (stackTraceFile.exists()) {
                tryOrNull {
                    serializer.decodeFromString<Set<StackTrace>>(stackTraceFile.readText())
                }?.let { addAll(it) }
            }
        }
    }
}
