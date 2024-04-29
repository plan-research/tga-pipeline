package org.plan.research.tga.tool.evosuite

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.plan.research.tga.core.benchmark.json.getJsonSerializer
import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.plan.research.tga.core.util.destroyRecursively
import org.vorpal.research.kthelper.buildProcess
import org.vorpal.research.kthelper.getJavaPath
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration

class EvoSuiteCliTool : TestGenerationTool {
    override val name: String = "EvoSuite"

    companion object {
        private const val EVOSUITE_VERSION = "1.0.5"
        private const val RESULTS_FILE_NAME = "serialized.json"
        private const val EVOSUITE_LOG = "evosuite.log"
        private val EVOSUITE_JAR_PATH = TGA_PIPELINE_HOME.resolve("lib", "evosuite-$EVOSUITE_VERSION.jar")
    }

    private lateinit var target: String
    private lateinit var root: Path
    private lateinit var classPath: List<Path>
    private lateinit var outputDirectory: Path


    override fun init(root: Path, classPath: List<Path>) {
        this.root = root
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.target = target
        this.outputDirectory = outputDirectory.also {
            it.toFile().mkdirs()
        }
        val resultsFile = outputDirectory.resolve(RESULTS_FILE_NAME)
        var process: Process? = null
        try {
            process = buildProcess(
                getJavaPath().toString(), "-jar",
                EVOSUITE_JAR_PATH.toString(),
                "-generateMOSuite",
                "-serializeResult",
                "-serializeResultPath", resultsFile.toString(),
                "-base_dir", outputDirectory.toString(),
                "-projectCP", classPath.joinToString(File.pathSeparator),
                "-Dnew_statistics=false",
                "-Dsearch_budget=${timeLimit.inWholeSeconds}",
                "-class", target,
                "-Dcatch_undeclared_exceptions=false",
                "-Dtest_naming_strategy=COVERAGE",
                "-Dalgorithm=DYNAMOSA",
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
            process?.destroyRecursively()
        }
    }

    override fun report(): TestSuite {
        val evoSuiteReport = outputDirectory.resolve(RESULTS_FILE_NAME)
        if (!evoSuiteReport.exists()) return TestSuite(outputDirectory, emptyList(), emptyList(), "", emptyList())

        val json = getJsonSerializer(pretty = false).parseToJsonElement(
            outputDirectory.resolve(RESULTS_FILE_NAME).readText()
        ).jsonObject

        val testSuiteCode = json["testSuiteCode"]!!.jsonPrimitive.content
        val testCases =
            json["testCaseList"]!!.jsonObject.entries.map { it.value.jsonObject["testCode"]!!.jsonPrimitive.content }
        val testPackage = getPackageFromTestSuiteCode(testSuiteCode)
        val testClassName = "EvoSuiteTest"

        val testCode = buildString {
            appendLine("package $testPackage;")
            appendLine()
            for (import in getImportsCodeFromTestSuiteCode(testSuiteCode)) {
                appendLine(import)
            }

            appendLine("public class $testClassName {")
            for (testCase in testCases) {
                appendLine(testCase)
            }
            appendLine("}")
        }

        val testFile = outputDirectory.resolve(*testPackage.split('.').toTypedArray()).also {
            it.toFile().mkdirs()
        }.resolve("$testClassName.java")
        testFile.bufferedWriter().use {
            it.write(testCode)
        }

        return TestSuite(
            outputDirectory,
            listOf("${testPackage}.$testClassName"),
            emptyList(),
            "",
            listOf(
                Dependency("junit", "junit", "4.13.2"),
                Dependency("org.mockito", "mockito-junit-jupiter", "5.11.0")
            )
        )
    }
}


/**
 * Retrieves the import's code from a given test suite code.
 *
 * @param testSuiteCode The test suite code from which to extract the import's code. If null, an empty string is returned.
 * @return The imports code extracted from the test suite code. If no imports are found or the result is empty after filtering, an empty string is returned.
 */
private fun getImportsCodeFromTestSuiteCode(testSuiteCode: String): Set<String> =
    testSuiteCode.replace("\r\n", "\n")
        .split("\n")
        .asSequence()
        .filter { "^import".toRegex() in it }
        .filterNot { "evosuite".toRegex() in it }
        .filterNotTo(mutableSetOf()) { "RunWith".toRegex() in it }

/**
 * Retrieves the package declaration from the given test suite code.
 *
 * @param testSuiteCode The generated code of the test suite.
 * @return The package declaration extracted from the test suite code, or an empty string if no package declaration was found.
 */
private fun getPackageFromTestSuiteCode(testSuiteCode: String): String =
    testSuiteCode.replace("\r\n", "\n")
        .split("\n")
        .filter { "^package".toRegex() in it }
        .joinToString("")
        .removePrefix("package ")
        .removeSuffix(";")
        .ifBlank { "" }
