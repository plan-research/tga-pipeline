package org.plan.research.tga.tool.evosuite

import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.getJavaPath
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import org.vorpal.research.kthelper.terminateOrKill
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class EvoSuiteCliTool : TestGenerationTool {
    override val name: String = "EvoSuite"

    companion object {
        private const val EVOSUITE_VERSION = "1.0.5"
        private const val EVOSUITE_DEPENDENCY_VERSION = "1.0.6"
        private const val EVOSUITE_LOG = "evosuite.log"
        private val EVOSUITE_JAR_PATH = TGA_PIPELINE_HOME.resolve("lib", "evosuite-$EVOSUITE_VERSION.jar")
    }

    private lateinit var root: Path
    private lateinit var classPath: List<Path>
    private lateinit var outputDirectory: Path

    override fun init(root: Path, classPath: List<Path>) {
        this.root = root
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.outputDirectory = outputDirectory.also {
            it.toFile().mkdirs()
        }
        var process: Process? = null
        try {
            process = buildProcess(
                getJavaPath().toString(), "-jar",
                EVOSUITE_JAR_PATH.toString(),
                "-generateMOSuite",
                "-base_dir", outputDirectory.toString(),
                "-projectCP", classPath.joinToString(File.pathSeparator),
                "-Dnew_statistics=false",
                "-Dsearch_budget=${timeLimit.inWholeSeconds}",
                "-class", target,
                "-Dcatch_undeclared_exceptions=false",
                "-Dtest_naming_strategy=COVERAGE",
                "-Dalgorithm=DYNAMOSA",
                "-Dno_runtime_dependency=true",
                "-Dcriterion=LINE:BRANCH:EXCEPTION:WEAKMUTATION:OUTPUT:METHOD:METHODNOEXCEPTION:CBRANCH",
            ) {
                redirectErrorStream(true)
                log.debug("Starting EvoSuite with command: {}", command())
            }
            log.debug("Configure reader for the EvoSuite process")
            outputDirectory.resolve(EVOSUITE_LOG).bufferedWriter().use { writer ->
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    writer.write(line)
                    writer.write("\n")
                }
            }
            log.debug("Waiting for the EvoSuite process...")
            process.waitFor()
            log.debug("EvoSuite process has merged")
        } catch (e: InterruptedException) {
            log.error("EvoSuite was interrupted on target $target")
        } finally {
            process?.terminateOrKill(attempts = 10U, waitTime = 500.milliseconds)
        }
    }

    override fun report(): TestSuite {
        val testSrcPath = outputDirectory.resolve("evosuite-tests")
        val tests = when {
            testSrcPath.exists() -> Files.walk(testSrcPath).filter { it.fileName.toString().endsWith(".java") }
                .map { testSrcPath.relativize(it).toString().replace('/', '.').removeSuffix(".java") }
                .toList()

            else -> emptyList()
        }
        return TestSuite(
            testSrcPath,
            tests,
            emptyList(),
            "",
            listOf(
                Dependency("junit", "junit", "4.13.2"),
                Dependency("org.evosuite", "evosuite-master", EVOSUITE_DEPENDENCY_VERSION),
                Dependency("org.evosuite", "evosuite-standalone-runtime", EVOSUITE_DEPENDENCY_VERSION),
            )
        )
    }
}
