package org.plan.research.tga.tool.testspark

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.plan.research.tga.core.config.buildOptions
import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.assert.exit
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration

private class TestSparkCliParser(args: List<String>) {
    private val cmd: CommandLine
    private val options: Options

    init {
        val parser = DefaultParser()
        options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option(null, "llm", true, "llm for test generation")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "llmToken", true, "token for LLM access")
                    .also { it.isRequired = false }
            )

            addOption(
                Option(null, "prompt", true, "prompt to use for test generation")
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

        cmd = try {
            parser.parse(options, args.toTypedArray())
        } catch (e: ParseException) {
            exit<CommandLine> {
                System.err.println("Error parsing command line arguments: ${e.message}")
                printHelp()
            }
        }

        getCmdValue("help")?.let {
            exit {
                printHelp()
            }
        }
    }

    fun getCmdValue(name: String): String? = cmd.getOptionValue(name)
    fun getCmdValue(name: String, default: String) = getCmdValue(name) ?: default

    fun printHelp() {
        println(helpString)
    }

    private val helpString: String
        get() {
            val helpFormatter = HelpFormatter()
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            helpFormatter.printHelp(pw, 80, "org/plan/research/tga/runner", null, options, 1, 3, null)
            return sw.toString()
        }
}

class TestSparkCliTool(args: List<String>) : TestGenerationTool {
    override val name = "TestSpark"

    private val argParser = TestSparkCliParser(args)
    private val promptFile = Files.createTempFile("prompt", ".txt")!!.also {
        it.writeText(argParser.getCmdValue("prompt", DEFAULT_PROMPT))
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
                "\"${src.toAbsolutePath()}\"", // path to project root
                "\"src/main/java/${
                    target.replace(
                        '.',
                        '/'
                    )
                }.java\"", // path to target source file relative to the project root
                "\"$target\"", // fully qualified name of the target
                "\"${classPath.joinToString(File.pathSeparator!!)}\"", // class path
                "\"${argParser.getCmdValue("llm", DEFAULT_LLM)}\"", // LLM to use
                "\"${argParser.getCmdValue("llmToken")!!}\"", // token to access chosen LLM
                "\"${promptFile.toAbsolutePath()}\"", // path to prompt file
                "\"${outputDirectory.toAbsolutePath()}\"", // path to output directory
                "\"${argParser.getCmdValue("spaceUser")!!}\"", // Space username
                "\"${argParser.getCmdValue("spaceToken")!!}\"", // token for accessing Space
            )
            log.debug("Starting TestSpark with command: {}", processBuilder.command())

            process = processBuilder.start()!!
            process.waitFor()
        } catch (e: InterruptedException) {
            log.error("TestSpark was interrupted on target $target")
        } finally {
            process?.destroy()
        }
    }

    override fun report(): TestSuite {
        val testSrcPath = outputDirectory.resolve("tests")
        val tests = when {
            testSrcPath.exists() -> Files.walk(testSrcPath).filter { it.fileName.toString().endsWith(".java") }
                .map { testSrcPath.relativize(it).toString().replace('/', '.').removeSuffix(".java") }
                .toList()

            else -> emptyList()
        }
        return TestSuite(
            testSrcPath,
            tests,
            listOf(
                Dependency("org.junit", "junit", "4.13.2"),
                Dependency("org.mockito", "mockito-junit-jupiter", "5.11.0")
            )
        )
    }
}
