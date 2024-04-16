package org.plan.research.tga.tool.testspark

import org.apache.commons.cli.Option
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration
import java.nio.charset.StandardCharsets
import kotlin.io.path.absolutePathString
import java.util.Locale


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
                Option(null, "prompt", true, "Filepath to the file with prompt to use for test generation")
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
        val promptFilepath = argParser.getCmdValue("prompt")

        val promptContent = if (promptFilepath == null) {
                log.debug("No prompt filepath provided, using default prompt")
                DEFAULT_PROMPT
            }
            else {
                log.debug("Provided prompt filepath: '$promptFilepath'")
                Files.readAllLines(Paths.get(promptFilepath), StandardCharsets.UTF_8)
                     .joinToString(separator = "\n", postfix = "\n")
            }

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
    }

    private lateinit var src: Path
    private lateinit var classPath: List<Path>
    private lateinit var outputDirectory: Path

    override fun init(src: Path, classPath: List<Path>) {
        this.src = src
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.outputDirectory = outputDirectory
        var process: Process? = null
        try {
            val processBuilder = ProcessBuilder(
                "bash", "${TEST_SPARK_HOME.resolve("runTestSpark.sh")}",
                "${src.toAbsolutePath()}", // path to project root
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
            )
            log.debug("Starting TestSpark with command: {}", processBuilder.command())

            process = processBuilder.start()!!
            log.debug("Waiting for the TestSpark process...")
            process.waitFor()
            log.debug("TestSpark process has merged")
        } catch (e: InterruptedException) {
            log.error("TestSpark was interrupted on target $target")
        } finally {
            log.debug(process?.inputStream?.bufferedReader()?.readText())
            process?.destroy()
        }
    }

    override fun report(): TestSuite {
        /**
         * TestSpark may generate non-compilable test cases together with those that compile (99% it is the case!),
         * thus it is important to sieve out the non-compilable test cases,
         * since thereafter the pipeline tries to compile the set of provided test files:
         * if any of them is non-compilable the coverage may not be generated.
         *
         * TestSpark insures that the test suite file named `GeneratedTest.java` will always contain only
         * compilable test cases, thus it is safe to use it in order to get a set of compilable test cases.
         */
        val testSrcPath = outputDirectory
        val tests = getCompilableTestCases(testSrcPath)
        val testCasesOnly = tests.filter { test -> !test.endsWith("GeneratedTest") }
        val testSuite = tests.first { test -> test.endsWith("GeneratedTest") }

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
        }
        else {
            log.debug("testSuiteFilepath is empty")
        }

        if (testSuiteFilepath.isEmpty) {
            /**
             * If the test suite file not found them return all the files present in the directory
             */
            val tests = when {
                testSrcPath.exists() -> Files.walk(testSrcPath)
                    .filter { it.fileName.toString().endsWith(".java") }
                    .map { testSrcPath.relativize(it).toString().replace('/', '.').removeSuffix(".java") }
                    .toList()
                else -> emptyList()
            }
            return tests
        }
        else {
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
                         * Test suite is supposed to contain only compilable test cases, thus such a condition
                         * insures compilability.
                         */
                        val testCaseName = it.split(".").last()
                        log.debug("Current test case fully qualified name: '{}', filename: '{}'", it, testCaseName)
                        (testCaseName == testSuiteFilename) || (isTestCaseUsedInTestSuite(testCaseName, testSuiteFilepath.get()))
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

        val result = Files.lines(testSuiteFilepath).anyMatch {
            line -> line.contains(requiredFunctionNameCapitalized) ||
                    line.contains(requiredFunctionNameLowerCased)
        }
        return result
    }
}
