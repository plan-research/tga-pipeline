package org.plan.research.tga.tool.testspark

import org.apache.commons.cli.Option
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.javaString
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.executeProcess
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.terminateOrKill
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


private class TestSparkCliParser(args: List<String>) : TgaConfig("TestSpark", options, args.toTypedArray()) {
    companion object {
        val options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option(null, "llm", true, "llm for test generation")
                    .also { it.isRequired = false }
            )

            addOption(
                Option(null, "llmToken", true, "token for LLM access")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "prompt", true, "prompt to use for test generation, as a string")
                    .also { it.isRequired = false }
            )

            addOption(
                Option(null, "spaceUser", true, "Space user name")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "spaceToken", true, "token for accessing Space")
                    .also { it.isRequired = true }
            )
        }
    }
}

class TestSparkCliTool(args: List<String>) : TestGenerationTool {
    override val name = "TestSpark"

    private val argParser = TestSparkCliParser(args)
    private val promptFile = Files.createTempFile("prompt", ".txt")!!.also {
        val promptContent = argParser.getCmdValue("prompt") ?: DEFAULT_PROMPT
        log.debug("promptContent: '$promptContent'")
        log.debug("Temp file where prompt is saved: '${it.absolutePathString()}'")
        it.writeText(promptContent)
    }

    companion object {
        private val TEST_SPARK_HOME: Path = Paths.get(System.getenv("TEST_SPARK_HOME"))
            ?: unreachable { log.error("No \$TEST_SPARK_HOME environment variable") }

        private const val DEFAULT_LLM = "GPT-4"
        private const val LANGUAGE = "\$LANGUAGE"
        private const val NAME = "\$NAME"
        private const val TESTING_PLATFORM = "\$TESTING_PLATFORM"
        private const val MOCKING_FRAMEWORK = "\$MOCKING_FRAMEWORK"
        private const val CODE = "\$CODE"
        private const val METHODS = "\$METHODS"
        private const val POLYMORPHISM = "\$POLYMORPHISM"
        private const val DEFAULT_PROMPT =
            """Generate unit tests in $LANGUAGE for $NAME to achieve 100% line coverage for this class.
Dont use @Before and @After test methods.
Make tests as atomic as possible.
All tests should be for $TESTING_PLATFORM.
In case of mocking, use $MOCKING_FRAMEWORK. But, do not use mocking for all tests.
Name all methods according to the template - [MethodUnderTest][Scenario]Test, and use only English letters.
The source code of class under test is as follows:
$CODE
$METHODS
$POLYMORPHISM"""

        private const val TEST_SPARK_LOG = "test-spark.log"
    }

    private lateinit var root: Path
    private lateinit var classPath: List<Path>
    private lateinit var outputDirectory: Path

    override fun init(root: Path, classPath: List<Path>) {
        this.root = root
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        // first, kill any running Gradle daemons left from the execution
        executeProcess("/bin/sh", "${TEST_SPARK_HOME.resolve("gradlew")}", "--stop")

        this.outputDirectory = outputDirectory.also {
            it.toFile().mkdirs()
        }
        var process: Process? = null
        try {
            process = buildProcess(
                "/bin/bash", "${TEST_SPARK_HOME.resolve("runTestSparkHeadless.sh")}",
                "${root.toAbsolutePath()}", // path to project root
                "src/main/java/${
                    target.replace(
                        '.',
                        '/'
                    )
                }.java", // path to target source file relative to the project root
                target, // fully qualified name of the target
                classPath.joinToString(File.pathSeparator!!), // class path
                "4", // JUnit version
                argParser.getCmdValue("llm", DEFAULT_LLM), // LLM to use
                argParser.getCmdValue("llmToken")!!, // token to access chosen LLM
                "${promptFile.toAbsolutePath()}", // path to prompt file
                "${outputDirectory.toAbsolutePath()}", // path to output directory
                "false", // disable coverage computation
                argParser.getCmdValue("spaceUser")!!, // Space username
                argParser.getCmdValue("spaceToken")!!, // token for accessing Space
            ) {
                redirectErrorStream(true)
                log.debug("Starting TestSpark with command: {}", command())
            }

            log.debug("Configure reader for the TestSpark process")
            outputDirectory.resolve(TEST_SPARK_LOG).bufferedWriter().use { writer ->
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val line = reader.readLine() ?: break
                    writer.write(line)
                    writer.write("\n")
                }
            }
            log.debug("Waiting for the TestSpark process...")
            process.waitFor()
            log.debug("TestSpark process has merged")
        } catch (e: InterruptedException) {
            log.error("TestSpark was interrupted on target $target")
        } finally {
            log.debug(process?.inputStream?.bufferedReader()?.readText())
            process?.terminateOrKill(attempts = 10U, waitTime = 500.milliseconds)
        }
    }

    override fun report(): TestSuite {
        // first, kill any running Gradle daemons left from the execution
        executeProcess("/bin/sh", "${TEST_SPARK_HOME.resolve("gradlew")}", "--stop")

        val testSrcPath = outputDirectory
        val tests = getTestCasesFromSrcPath(testSrcPath)
        return TestSuite(
            testSrcPath,
            tests,
            emptyList(),
            listOf(
                Dependency("junit", "junit", "4.13.2"),
                Dependency("org.mockito", "mockito-junit-jupiter", "5.11.0")
            )
        )
    }

    private fun getTestCasesFromSrcPath(testSrcPath: Path): List<String> {
        // collect only individual test cases, so that we are able to compute the compilation rate later
        val individualTestCases = Files.walk(testSrcPath)
            .filter { it.fileName.toString().endsWith(".java") }
            .filter { !it.fileName.toString().endsWith("GeneratedTest.java") }
            .map { testSrcPath.relativize(it).toString().javaString.removeSuffix(".java") }
            .collect(Collectors.toList())
        log.debug("TestSpark generated test cases: {}", individualTestCases)
        return individualTestCases
    }
}
