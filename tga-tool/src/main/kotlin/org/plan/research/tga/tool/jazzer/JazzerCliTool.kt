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
import org.vorpal.research.kthelper.terminateOrKill
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.plan.research.tga.core.dependency.Dependency
import org.vorpal.research.kthelper.resolve

class JazzerCliTool : TestGenerationTool {
    override val name = "Jazzer"
    private lateinit var classPath: List<Path>
    private lateinit var outputDirectory: Path
    private lateinit var packageName: String

    companion object {
        private val JAZZER_HOME = Paths.get(System.getenv("JAZZER_HOME"))
            ?: unreachable { log.error("No \$JAZZER_HOME environment variable") }
        private val JACOCO_AGENT_PATH = TGA_PIPELINE_HOME.resolve("lib").resolve("jacocoagent.jar")
        private val MAX_TARGETS = 8
    }


    override fun init(root: Path, classPath: List<Path>) {
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.outputDirectory = outputDirectory
        this.packageName = target.substringBeforeLast(".")
        while (!outputDirectory.exists()) {
            log.debug("Creating directory {}", outputDirectory)
            outputDirectory.toFile().mkdirs()
        }
        log.debug("Directory {} exists: {}", outputDirectory, outputDirectory.exists())

        val targets = parseMethodSignatures(target)
        if (targets.isEmpty()) {
            log.debug("No targets found, stopping Jazzer")
            return
        }

        val processes = targets.mapIndexed { index, t ->
            log.debug("Starting Jazzer on target {}", t)
            val testName = t.substringBefore("(")
                .replace("..", "_")
                .replace(".", "_")
            val execFile = outputDirectory.resolve("${testName}_$index.exec")
            val logFile = this.outputDirectory.resolve("${testName}_$index.log")
            val command = listOf(
                "timeout",
                "${timeLimit.inWholeSeconds}",
                "${JAZZER_HOME.resolve("jazzer")}",
                "--cp=${classPath.joinToString(File.pathSeparator!!)}",
                "--autofuzz=$t",
                "--coverage_dump=${execFile.toAbsolutePath()}",
//                "--jvm_args=\"-javaagent:${JACOCO_AGENT_PATH.toAbsolutePath()}=destfile=${execFile.toAbsolutePath()}\"",
                "--keep_going=100",
            )
            log.debug(
                "Starting Jazzer process with command ${
                    command.joinToString(" ", prefix = "\n")
                }"
            )
            buildProcess(command) {
                redirectErrorStream(true)
                redirectOutput(logFile.toFile())
            }
        }
        log.debug("All the jazzer agents started")
        Thread.sleep((timeLimit + 5.seconds).inWholeMilliseconds)
        for (process in processes) {
            process.terminateOrKill(attempts = 10U, waitTime = 500.milliseconds)
        }
        log.debug("Jazzer processes finished")
    }

    override fun report(): TestSuite {
        val testSrcPath = outputDirectory.resolve("tests")
        val path = packageName.split(".").toTypedArray()
        val tests = File(".").listFiles().orEmpty()
            .filter { it.name.startsWith("CrashTest_") && it.name.endsWith(".java") }
            .map { it.toPath().toAbsolutePath() }
            .map { test ->
                val file = testSrcPath.resolve(*path).resolve(test.name)
                file.parent.toFile().mkdirs()
                val code = "package $packageName;\n${test.readText()}\n"
                file.bufferedWriter().use {
                    it.write(code)
                }
                "$packageName.${test.nameWithoutExtension}"
            }

        File(".").listFiles().orEmpty()
            .filter {
                it.name.startsWith("CrashTest_")
                        || it.name.startsWith("crash-")
                        || it.name.startsWith("slow-unit-")
            }
            .forEach { it.delete() }

        return TestSuite(
            testSrcPath,
            tests,
            emptyList(),
            listOf(
                Dependency("junit", "junit", "4.13.2"),
            ),
        )
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
        return (getClassSignatures(klass) + klass.innerClasses.filter { it.value.isPublic }.keys.filter { it.isPublic }
            .flatMap { getClassSignatures(it) })
            .sortedByDescending { it.first }
            .take(MAX_TARGETS)
            .map { it.first }
    }

    private fun getClassSignatures(klass: Class): List<Pair<String, Int>> = klass.allMethods
        .filter { it.isPublic && !(it.isAbstract || it.isNative || it.isStaticInitializer) }
        .map {
            val argTypes = it.argTypes.joinToString(separator = ",", prefix = "(", postfix = ")") { type ->
                when (val str = type.toString().javaString) {
                    "bool" -> "boolean"
                    else -> str
                }
            }
            when {
                it.isConstructor -> "${klass.fullName.javaString}::new$argTypes"
                else -> "${klass.fullName.javaString}::${it.name}$argTypes"
            } to it.body.flatten().size
        } +
            klass.innerClasses.keys
                .filter { it.isPublic }
                .flatMap { getClassSignatures(it) }
}
