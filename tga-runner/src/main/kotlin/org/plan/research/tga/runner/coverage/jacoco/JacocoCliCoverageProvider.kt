package org.plan.research.tga.runner.coverage.jacoco

import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.coverage.BasicCoverageInfo
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassCoverageInfo
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.CoverageProvider
import org.plan.research.tga.core.coverage.ExtendedCoverageInfo
import org.plan.research.tga.core.coverage.Fraction
import org.plan.research.tga.core.coverage.InstructionId
import org.plan.research.tga.core.coverage.LineId
import org.plan.research.tga.core.coverage.MethodCoverageInfo
import org.plan.research.tga.core.coverage.MethodId
import org.plan.research.tga.core.coverage.TestSuiteCoverage
import org.plan.research.tga.core.dependency.DependencyManager
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.TGA_PIPELINE_HOME
import org.plan.research.tga.core.util.asmString
import org.plan.research.tga.runner.compiler.SystemJavaCompiler
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.deleteOnExit
import org.vorpal.research.kthelper.executeProcess
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.resolve
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists


class JacocoCliCoverageProvider(
    private val dependencyManager: DependencyManager
) : CoverageProvider {
    private val tgaTempDir = Files.createTempDirectory("tga-runner").also {
        deleteOnExit(it)
    }.toAbsolutePath()
    private val compiledDir: Path = tgaTempDir.resolve("compiled").toAbsolutePath().also {
        it.toFile().mkdirs()
    }

    companion object {
        private val JACOCO_CLI_PATH = TGA_PIPELINE_HOME.resolve("lib").resolve("jacococli.jar")
        private val JACOCO_AGENT_PATH = TGA_PIPELINE_HOME.resolve("lib").resolve("jacocoagent.jar")
    }


    override fun computeCoverage(benchmark: Benchmark, testSuite: TestSuite): TestSuiteCoverage {
        log.debug("Computing coverage: compiledDir='{}'", compiledDir)
        val tests = testSuite.tests.associateWith { testSuite.testSrcPath.resolve(it.asmString + ".java") }
        val testDependencies =
            testSuite.testSrcDependencies.mapToArray { testSuite.testSrcPath.resolve(it.asmString + ".java") }

        val classPath = benchmark.classPath + testSuite.dependencies.flatMap { dependencyManager.findDependency(it) }
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
            tests.size,
            compilableTestCases.size,
            "%.2f".format(100.0 * compilationRate.ratio)
        )


        val pkg = benchmark.klass.substringBeforeLast('.')
        val name = benchmark.klass.substringAfterLast('.')
        val fullTestCP = listOf(*classPath.toTypedArray(), compiledDir)
        val execFiles = mutableListOf<Path>()

        for ((testName, _) in compilableTestCases) {
            val execFile = testSuite.testSrcPath.resolve("$testName.exec")
            executeProcess(
                "java",
                "-cp",
                fullTestCP.joinToString(separator = File.pathSeparator),
                "-javaagent:${JACOCO_AGENT_PATH.toAbsolutePath()}=destfile=${execFile.toAbsolutePath()}",
                "org.junit.runner.JUnitCore",
                testName,
            )
            execFiles.add(execFile)
        }

        val xmlCoverageReport = testSuite.testSrcPath.resolve("coverage.xml")
        executeProcess(
            "java",
            "-jar",
            JACOCO_CLI_PATH.toString(),
            "report",
            *execFiles.mapToArray { it.toString() },
            "--classfiles",
            "${benchmark.bin.resolve(*pkg.split('.').toTypedArray(), "$name.class")}",
            "--sourcefiles",
            "${benchmark.src.resolve(*pkg.split('.').toTypedArray(), "$name.java")}",
            "--xml",
            xmlCoverageReport.toString(),
        )

        return TestSuiteCoverage(
            compilationRate,
            setOf(parseCoverageXml(benchmark, xmlCoverageReport))
        )
    }

    @Suppress("UNUSED_VARIABLE")
    private fun parseCoverageXml(benchmark: Benchmark, path: Path): ClassCoverageInfo {
        if (!path.exists()) return ClassCoverageInfo(ClassId(benchmark.klass), emptySet())
        val pkg = benchmark.klass.substringBeforeLast('.')
        val name = benchmark.klass.substringAfterLast('.')

        val builder = DocumentBuilderFactory.newInstance().also {
            it.isValidating = false
            it.isNamespaceAware = true
            it.setFeature("http://xml.org/sax/features/namespaces", false)
            it.setFeature("http://xml.org/sax/features/validation", false)
            it.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
            it.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder()
        val doc = builder.parse(path.toFile())
        doc.documentElement.normalize()

        val reportElement = doc.getElementByTag("report", 0)
        val packageElement = reportElement.getElementByTag("package", 0)
        ktassert(packageElement.getAttribute("name") == pkg.replace('.', '/'))
        val classElement = packageElement.getElementByTag("class", 0)
        ktassert(classElement.getAttribute("name") == benchmark.klass.replace('.', '/'))

        val methodCoverages = mutableSetOf<MethodCoverageInfo>()
        val methodBounds = classElement.getAllElementsByTag("method").mapNotNull { methodElement ->
            val methodName = methodElement.getAttribute("name")
            val methodDescriptor = methodElement.getAttribute("desc")
            val counters = methodElement.getAllElementsByTag("counter").associate {
                val covered = it.getAttribute("covered").toInt()
                val missed = it.getAttribute("missed").toInt()
                it.getAttribute("type") to Fraction(covered, covered + missed)
            }
            when {
                /**
                 * Special case for default constructors and static initializers
                 * because they may be non-continuous in code
                 */
                methodName == "<init>" && methodDescriptor == "()V" || methodName == "<clinit>" -> {
                    val inst = counters["INSTRUCTION"] ?: Fraction(0, 0)
                    val line = counters["LINE"] ?: Fraction(0, 0)
                    val branch = counters["BRANCH"] ?: Fraction(0, 0)
                    methodCoverages += MethodCoverageInfo(
                        MethodId(methodName, methodDescriptor),
                        BasicCoverageInfo(inst.numerator.toUInt(), inst.denominator.toUInt()),
                        BasicCoverageInfo(line.numerator.toUInt(), line.denominator.toUInt()),
                        BasicCoverageInfo(branch.numerator.toUInt(), branch.denominator.toUInt()),
                    )
                    null
                }

                else -> {
                    val startLine = methodElement.getAttribute("line").toInt()
                    val length = counters["LINE"]!!.denominator
                    Triple(MethodId(methodName, methodDescriptor), startLine, length)
                }
            }
        }.sortedBy { it.second }

        val sourceFileElement = packageElement.getElementByTag("sourcefile", 0)
        val sourceFileName = sourceFileElement.getAttribute("name")
        ktassert(sourceFileName == classElement.getAttribute("sourcefilename"))

        var length = -1
        var index = 0
        var lines = mutableMapOf<LineId, Boolean>()
        var instructions = mutableMapOf<InstructionId, Boolean>()
        var branches = mutableMapOf<BranchId, Boolean>()

        for (lineElement in sourceFileElement.getAllElementsByTag("line").sortedBy { it.getAttribute("nr").toInt() }) {
            val lineNumber = lineElement.getAttribute("nr").toInt()
            if (lineNumber == methodBounds[index].second) {
                length = 0
            }
            if (length < 0) continue

            val missedInstructions = lineElement.getAttribute("mi").toInt()
            val coveredInstructions = lineElement.getAttribute("ci").toInt()
            val totalInstructions = missedInstructions + coveredInstructions
            val missedBranches = lineElement.getAttribute("mb").toInt()
            val coveredBranches = lineElement.getAttribute("cb").toInt()
            val totalBranches = missedBranches + coveredBranches

            if (totalInstructions == 0) continue
            val lineId = LineId(sourceFileName, lineNumber.toUInt())
            lines[lineId] = (coveredInstructions > 0)

            for (inst in 0 until totalInstructions) {
                val id = InstructionId(lineId, inst.toUInt())
                instructions[id] = (inst < coveredInstructions)
            }

            for (branch in 0 until totalBranches) {
                val id = BranchId(lineId, branch.toUInt())
                branches[id] = (branch < coveredBranches)
            }
            ++length

            if (length == methodBounds[index].third) {
                methodCoverages += MethodCoverageInfo(
                    methodBounds[index].first,
                    ExtendedCoverageInfo(instructions),
                    ExtendedCoverageInfo(lines),
                    ExtendedCoverageInfo(branches),
                )
                instructions = mutableMapOf()
                lines = mutableMapOf()
                branches = mutableMapOf()
                length = -1
                ++index
            }
        }

        return ClassCoverageInfo(ClassId(benchmark.klass), methodCoverages)
    }

    private fun Document.getElementByTag(name: String, index: Int): Element =
        this.getElementsByTagName(name).item(index) as Element

    private fun Element.getElementByTag(name: String, index: Int): Element =
        this.getElementsByTagName(name).item(index) as Element

    private fun Element.getAllElementsByTag(name: String): Iterable<Element> {
        val elements = this.getElementsByTagName(name)
        return (0 until elements.length).map { elements.item(it) as Element }
    }
}
