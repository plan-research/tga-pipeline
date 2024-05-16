package org.plan.research.tga.tool.testspark

import org.apache.commons.cli.Option
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
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
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.streams.toList
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
        private const val DEFAULT_PROMPT =
            "Generate unit tests in \$LANGUAGE for \$NAME to achieve 100% line coverage for this class.\\n" +
                    "Dont use @Before and @After test methods.\\n" +
                    "Make tests as atomic as possible.\\n" +
                    "All tests should be for \$TESTING_PLATFORM.\\n" +
                    "In case of mocking, use \$MOCKING_FRAMEWORK. But, do not use mocking for all tests.\\n" +
                    "Name all methods according to the template - [MethodUnderTest][Scenario]Test, and use only English letters.\\n" +
                    "The source code of class under test is as follows:\\n" +
                    "\$CODE\\n" +
                    "Here are some information about other methods and classes used by the class under test. Only use them for creating objects, not your own ideas.\\n" +
                    "\$METHODS\\n" +
                    "\$POLYMORPHISM"

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
        this.outputDirectory = outputDirectory.also {
            it.toFile().mkdirs()
        }
        var process: Process? = null
        try {
            process = buildProcess(
                "/bin/bash", "${TEST_SPARK_HOME.resolve("runTestSpark.sh")}",
                "${root.toAbsolutePath()}", // path to project root
                "src/main/java/${
                    target.replace(
                        '.',
                        '/'
                    )
                }.java", // path to target source file relative to the project root
                target, // fully qualified name of the target
                classPath.joinToString(File.pathSeparator!!), // class path
                argParser.getCmdValue("llm", DEFAULT_LLM), // LLM to use
                argParser.getCmdValue("llmToken")!!, // token to access chosen LLM
                "${promptFile.toAbsolutePath()}", // path to prompt file
                "${outputDirectory.toAbsolutePath()}", // path to output directory
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
        // first of all, kill any running Gradle daemons left from the execution
        executeProcess("/bin/sh", "${TEST_SPARK_HOME.resolve("gradlew")}", "--stop")
        /**
         * TestSpark may generate non-compilable test cases together with those that compile (99% it is the case!),
         * thus it is important to sieve out the non-compilable test cases,
         * since thereafter the pipeline tries to compile the set of provided test files:
         * if any of them is non-compilable, the coverage may not be generated.
         *
         * TestSpark insures that the test suite file named `GeneratedTest.java` will always contain only
         * compilable test cases, thus it is safe to use it to get a set of compilable test cases.
         */
        val testSrcPath = outputDirectory
        val tests = getCompilableTestCases(testSrcPath)
        val testCasesOnly = tests.filter { test -> !test.endsWith("GeneratedTest") }
        val testSuite = tests.firstOrNull { test -> test.endsWith("GeneratedTest") } ?: ""

        log.debug("Compilable tests {}: {}", tests.size, tests)
        log.debug("Compilable test cases {}: {}", testCasesOnly.size, testCasesOnly)
        log.debug("Test suite: {}", testSuite)

        return TestSuite(
            testSrcPath,
            tests,
            testCasesOnly,
            testSuite,
            listOf(
                Dependency("junit", "junit", "4.13.2"),
                Dependency("org.mockito", "mockito-junit-jupiter", "5.11.0")
            )
        )
    }

    private fun getCompilableTestCases(testSrcPath: Path): List<String> {
        val testSuiteFilepath = Files.walk(testSrcPath)
            .filter { path -> path.toString().endsWith("GeneratedTest.java") }
            .findFirst()

        log.debug("testSrcPath='{}'", testSrcPath)
        if (testSuiteFilepath.isPresent) {
            log.debug("testSuiteFilepath={}", testSuiteFilepath.get())
        } else {
            log.debug("testSuiteFilepath is empty")
        }

        if (testSuiteFilepath.isEmpty) {
            /**
             * If the test suite file is not found, then return all the files present in the directory
             */
            val tests = when {
                testSrcPath.exists() -> Files.walk(testSrcPath)
                    .filter { it.fileName.toString().endsWith(".java") }
                    .map { testSrcPath.relativize(it).toString().replace('/', '.').removeSuffix(".java") }
                    .toList()

                else -> emptyList()
            }
            return tests
        } else {
            val testSuiteFilename = testSuiteFilepath.get().fileName.toString().removeSuffix(".java")

            val tests = when {
                testSrcPath.exists() -> Files.walk(testSrcPath)
                    .toList()
                    .filter { it.fileName.toString().endsWith(".java") }
                    .map {
                        testSrcPath.relativize(it).toString()
                            .replace('/', '.')
                            .removeSuffix(".java")
                    }
                    /** filtering out non-compilable test cases **/
                    .filter {
                        /**
                         * Current test is either a test suite or a test case which is contained inside the test suite.
                         * The test suite is supposed to contain only compilable test cases, thus such a condition
                         * insures successful compilation.
                         */
                        val testCaseName = it.split(".").last()
                        log.debug("Current test case fully qualified name: '{}', filename: '{}'", it, testCaseName)
                        (testCaseName == testSuiteFilename) || (isTestCaseUsedInTestSuite(
                            testCaseName,
                            testSuiteFilepath.get()
                        ))
                    }
                    .toList()

                else -> emptyList()
            }
            val compilableTestCasesCount = Files.lines(testSuiteFilepath.get())
                .filter { line -> line.contains("@Test") }
                .count()
            assert(tests.size.toLong() == 1 + compilableTestCasesCount)
            return tests
        }
    }

    private fun isTestCaseUsedInTestSuite(testCaseName: String, testSuiteFilepath: Path): Boolean {
        // some test cases may start with an uppercase letter (e.g., `DBAppConstructorTest`)
        val requiredFunctionNameCapitalized = testCaseName.removePrefix("Generated")
        val requiredFunctionNameLowerCased = requiredFunctionNameCapitalized
            .replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() }

        val result = Files.lines(testSuiteFilepath).anyMatch { line ->
            line.contains(requiredFunctionNameCapitalized) ||
                    line.contains(requiredFunctionNameLowerCased)
        }
        return result
    }
}
