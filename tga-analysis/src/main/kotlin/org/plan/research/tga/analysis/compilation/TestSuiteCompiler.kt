package org.plan.research.tga.analysis.compilation

import kotlinx.serialization.Serializable
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.benchmark.json.ListOfPathSerializer
import org.plan.research.tga.core.benchmark.json.MapOfStringPathSerializer
import org.plan.research.tga.core.benchmark.json.PathAsStringSerializer
import org.plan.research.tga.core.compiler.SystemJavaCompiler
import org.plan.research.tga.core.coverage.Fraction
import org.plan.research.tga.core.dependency.DependencyManager
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.asmString
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.deleteOnExit
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class CompilationResult(
    @Serializable(with = PathAsStringSerializer::class)
    val compiledDir: Path,
    @Serializable(with = MapOfStringPathSerializer::class)
    val compilableTests: Map<String, Path>,
    @Serializable(with = ListOfPathSerializer::class)
    val fullClassPath: List<Path>,
    val compilationRate: Fraction,
)

class TestSuiteCompiler(private val dependencyManager: DependencyManager) {
    private val tgaTempDir = Files.createTempDirectory("tga-runner").also {
        deleteOnExit(it)
    }.toAbsolutePath()

    fun compile(benchmark: Benchmark, testSuite: TestSuite): CompilationResult {
        val compiledDir: Path = Files.createTempDirectory(tgaTempDir, "compiled").toAbsolutePath().also {
            it.toFile().mkdirs()
        }
        log.debug("Computing coverage: compiledDir='{}'", compiledDir)
        val tests = testSuite.tests.associateWith { testSuite.testSrcPath.resolve(it.asmString + ".java") }
        val testDependencies = testSuite.testSrcDependencies.mapToArray {
            testSuite.testSrcPath.resolve(it.asmString + ".java")
        }

        val classPath = listOf(
            benchmark.bin,
            *benchmark.classPath.toTypedArray(),
            compiledDir,
            *testSuite.dependencies.flatMap { dependencyManager.findDependency(it) }.toTypedArray(),
        )
        val compiler = SystemJavaCompiler(classPath)

        val compilableTestCases = mutableMapOf<String, Path>()

        for ((name, path) in tests) {
            log.debug("Attempting to compile test {}", name)

            try {
                val result = compiler.compile(listOf(path, *testDependencies), compiledDir)
                compilableTestCases[name] = path
                log.debug("Compilation succeeded with result: {}", result)
            } catch (e: Throwable) {
                log.error(e)
            }
        }
        val compilationRate = Fraction(compilableTestCases.size, tests.size)
        log.debug(
            "{} tests of {} are successfully compiled, compilation rate {}%",
            compilableTestCases.size,
            tests.size,
            "%.2f".format(100.0 * compilationRate.ratio)
        )
        val fullTestCP = listOf(*classPath.toTypedArray(), compiledDir)
        return CompilationResult(compiledDir, compilableTestCases, fullTestCP, compilationRate)
    }
}
