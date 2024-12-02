package org.plan.research.tga.tool.jazzer

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.plan.research.tga.core.util.asmString
import org.plan.research.tga.core.util.javaString
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import org.vorpal.research.kthelper.terminateOrKill
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JazzerCliTool : TestGenerationTool {
    override val name = "Jazzer"
    private lateinit var classPath: List<Path>
    private lateinit var outputDirectory: Path

    companion object {
        private val JAZZER_HOME = Paths.get(System.getenv("JAZZER_HOME"))
            ?: unreachable { log.error("No \$KEX_HOME environment variable") }
        private val JACOCO_AGENT_PATH = TGA_PIPELINE_HOME.resolve("lib").resolve("jacocoagent.jar")
    }


    override fun init(root: Path, classPath: List<Path>) {
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.outputDirectory = outputDirectory
        while (!outputDirectory.exists()) {
            log.debug("Creating directory {}", outputDirectory)
            outputDirectory.toFile().mkdirs()
        }
        log.debug("Directory {} exists: {}", outputDirectory, outputDirectory.exists())
        val targets = parseMethodSignatures(target)
        val processes = targets.mapIndexed { index, t ->
            val testName = t.substringBefore("(")
                .replace("..", "_")
                .replace(".", "_")
            val execFile = outputDirectory.resolve("${testName}_$index.exec")
            buildProcess(
                "java",
                "-javaagent:${JACOCO_AGENT_PATH.toAbsolutePath()}=destfile=${execFile.toAbsolutePath()}",
                "-jar",
                "${JAZZER_HOME.resolve("jazzer_standalone.jar")}",
                "--cp=${classPath.joinToString(File.pathSeparator!!)}",
                "--autofuzz=\"$t\""
            )
        }
        log.debug("All the jazzer agents started")
        Thread.sleep(timeLimit.inWholeMilliseconds)
        for (process in processes) {
            process.terminateOrKill(attempts = 10U, waitTime = 500.milliseconds)
        }
        log.debug("Jazzer processes finished")
    }

    override fun report(): TestSuite {
        return TestSuite(outputDirectory, emptyList(), emptyList(), emptyList())
    }


//    private fun Path.resolveClass(klass: String): Path? {
//        val pkg = klass.substringBeforeLast('.').split('.')
//        val name = klass.substringAfterLast('.')
//        var current = this.resolve(*pkg.toTypedArray())
//        current = current.resolve("${name}.java")
//        return when {
//            current.exists() -> current
//            else -> null
//        }
//    }

    private fun parseMethodSignatures(target: String): List<String> {
        val cm = ClassManager().also { it.initialize(classPath.mapNotNull { path -> path.asContainer() }) }
        val klass = cm[target.asmString]
        return getClassSignatures(klass) + klass.innerClasses.filter { it.value.isPublic }.keys.filter { it.isPublic }
            .flatMap { getClassSignatures(it) }
    }

    private fun getClassSignatures(klass: Class): List<String> = klass.allMethods
        .filter { it.isPublic && !(it.isAbstract || it.isNative || it.isStaticInitializer) }
        .map {
            val argTypes = it.argTypes.joinToString(separator = ",", prefix = "(", postfix = ")") { type ->
                type.toString().javaString
            }
            when {
                it.isConstructor -> "${klass.fullName.javaString}::new$argTypes"
                else -> "${klass.fullName.javaString}::${it.name}$argTypes"
            }
        } +
            klass.innerClasses.keys
                .filter { it.isPublic }
                .flatMap { getClassSignatures(it) }
}