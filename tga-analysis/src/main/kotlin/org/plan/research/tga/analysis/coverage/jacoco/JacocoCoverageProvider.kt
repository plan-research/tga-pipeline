package org.plan.research.tga.analysis.coverage.jacoco

import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore
import org.jacoco.core.instr.Instrumenter
import org.jacoco.core.runtime.IExecutionDataAccessorGenerator
import org.jacoco.core.runtime.LoggerRuntime
import org.jacoco.core.runtime.RuntimeData
import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.analysis.coverage.CoverageProvider
import org.plan.research.tga.core.benchmark.Benchmark
import org.plan.research.tga.core.coverage.BranchId
import org.plan.research.tga.core.coverage.ClassCoverageInfo
import org.plan.research.tga.core.coverage.ClassId
import org.plan.research.tga.core.coverage.ExtendedCoverageInfo
import org.plan.research.tga.core.coverage.InstructionId
import org.plan.research.tga.core.coverage.LineId
import org.plan.research.tga.core.coverage.MethodCoverageInfo
import org.plan.research.tga.core.coverage.MethodId
import org.plan.research.tga.core.coverage.TestSuiteCoverage
import org.plan.research.tga.core.tool.TestSuite
import org.plan.research.tga.core.util.asArray
import org.plan.research.tga.core.util.asmString
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes

fun ExecutionDataStore.deepCopy(): ExecutionDataStore {
    val executionDataCopy = ExecutionDataStore()
    for (content in contents) {
        val ed = ExecutionData(content.id, content.name, content.probes.copyOf())
        executionDataCopy.put(ed)
    }
    return executionDataCopy
}

@Deprecated("Use external jacoco runner")
class JacocoCoverageProvider : CoverageProvider {
    override fun computeCoverage(
        benchmark: Benchmark,
        testSuite: TestSuite,
        compilationResult: CompilationResult
    ): TestSuiteCoverage {
        val runtime = LoggerRuntime()
        val classLoader = InstrumentingPathClassLoader(
            compilationResult.fullClassPath,
            setOf(benchmark.klass),
            runtime
        )

        val datum = mutableMapOf<Path, ExecutionDataStore>()
        val data = RuntimeData()
        runtime.startup(data)

        for ((testName, testPath) in compilationResult.compilableTests) {
            try {
                val testClass = classLoader.loadClass(testName)
                val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")

                @Suppress("DEPRECATION")
                val jc = jcClass.newInstance()
                val computerClass = classLoader.loadClass("org.junit.runner.Computer")
                @Suppress("DEPRECATION")
                jcClass.getMethod("run", computerClass, Class::class.java.asArray())
                    .invoke(jc, computerClass.newInstance(), arrayOf(testClass))

                val executionData = ExecutionDataStore()
                data.collect(executionData, SessionInfoStore(), false)
                datum[testPath] = executionData.deepCopy()
                data.reset()
            } catch (e: Throwable) {
                log.error("Error when executing test $testName, ", e)
            }
        }

        runtime.shutdown()

        val mergedExecutionData = ExecutionDataStore()
        for ((_, testPath) in compilationResult.compilableTests) {
            val executions = datum[testPath] ?: continue
            for (d in executions.contents) {
                mergedExecutionData.put(d)
            }
        }

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(mergedExecutionData, coverageBuilder)
        for ((className, byteArray) in classLoader.originalBytecode) {
            byteArray.inputStream().use {
                tryOrNull {
                    analyzer.analyzeClass(it, className)
                }
            }
        }

        val methods = coverageBuilder.classes.mapTo(mutableSetOf()) {
            it.methods.mapTo(mutableSetOf()) { method ->
                val lines = mutableMapOf<LineId, Boolean>()
                val instructions = mutableMapOf<InstructionId, Boolean>()
                val branches = mutableMapOf<BranchId, Boolean>()

                for (lineNumber in method.firstLine..method.lastLine) {
                    val line = method.getLine(lineNumber)
                    if (line.instructionCounter.totalCount == 0) continue

                    val lineId = LineId(it.sourceFileName, lineNumber.toUInt())
                    lines[lineId] = when (line.status) {
                        ICounter.PARTLY_COVERED, ICounter.FULLY_COVERED -> true
                        else -> false
                    }

                    for (instNum in 0 until line.instructionCounter.totalCount) {
                        val instId = InstructionId(lineId, instNum.toUInt())
                        instructions[instId] = when {
                            instNum < line.instructionCounter.coveredCount -> true
                            else -> false
                        }
                    }

                    for (branchNum in 0 until line.branchCounter.totalCount) {
                        val branchId = BranchId(lineId, branchNum.toUInt())
                        branches[branchId] = when {
                            branchNum < line.branchCounter.coveredCount -> true
                            else -> false
                        }
                    }
                }

                MethodCoverageInfo(
                    MethodId(method.name, method.desc),
                    ExtendedCoverageInfo(instructions),
                    ExtendedCoverageInfo(lines),
                    ExtendedCoverageInfo(branches)
                )
            }
        }.firstOrNull() ?: return TestSuiteCoverage(compilationResult.compilationRate, emptySet())

        return TestSuiteCoverage(
            compilationResult.compilationRate,
            setOf(ClassCoverageInfo(ClassId(benchmark.klass), methods))
        )
    }

    class InstrumentingPathClassLoader(
        private val paths: List<Path>,
        private val targets: Set<String>,
        runtime: IExecutionDataAccessorGenerator,
        parent: ClassLoader = InstrumentingPathClassLoader::class.java.classLoader
    ) : ClassLoader(parent) {
        private val instrumenter = Instrumenter(runtime)
        private val cache = hashMapOf<String, Class<*>>()
        val originalBytecode = hashMapOf<String, ByteArray>()

        private fun instrument(name: String, bytes: ByteArray): ByteArray {
            return instrumenter.instrument(bytes.inputStream(), name)
        }

        private fun readClassFromJar(name: String, path: Path): ByteArray? {
            val fileName = name.asmString + ".class"
            val jarFile = JarFile(path.toFile())
            val entry = jarFile.getJarEntry(fileName) ?: return null
            return jarFile.getInputStream(entry).readBytes()
        }

        private fun readClassFromDirectory(name: String, path: Path): ByteArray? {
            val fileName = name.asmString + ".class"
            val resolved = path.resolve(fileName)
            return when {
                resolved.exists() -> resolved.readBytes()
                else -> null
            }
        }

        private fun defineClass(name: String, bytes: ByteArray): Class<*> {
            val klass = defineClass(name, bytes, 0, bytes.size)
            cache[name] = klass
            return klass
        }

        override fun loadClass(name: String): Class<*> = synchronized(this.getClassLoadingLock(name)) {
            if (name in cache) return cache[name]!!
            for (path in paths) {
                val bytes = when {
                    path.isDirectory() -> readClassFromDirectory(name, path)
                    path.fileName.toString().endsWith(".jar") -> readClassFromJar(name, path)
                    else -> null
                }
                if (bytes != null) {
                    val instrumentedBytes = when (name) {
                        in targets -> {
                            originalBytecode[name] = bytes
                            instrument(name, bytes)
                        }

                        else -> bytes
                    }
                    return defineClass(name, instrumentedBytes)
                }
            }
            return parent?.loadClass(name) ?: throw ClassNotFoundException()
        }
    }
}
