package org.plan.research.tga.coverage.jacoco

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
import org.plan.research.tga.benchmark.Benchmark
import org.plan.research.tga.compiler.SystemJavaCompiler
import org.plan.research.tga.coverage.BranchId
import org.plan.research.tga.coverage.ClassCoverageInfo
import org.plan.research.tga.coverage.CoverageInfo
import org.plan.research.tga.coverage.CoverageProvider
import org.plan.research.tga.coverage.InstructionId
import org.plan.research.tga.coverage.LineId
import org.plan.research.tga.coverage.MethodCoverageInfo
import org.plan.research.tga.tool.TestSuite
import org.vorpal.research.kfg.Package
import org.vorpal.research.kthelper.deleteOnExit
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.lang.reflect.Array
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

val String.asmString get() = replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)

fun Path.fullyQualifiedName(base: Path): String =
    relativeTo(base).toString()
        .removePrefix("..")
        .removePrefix(File.separatorChar.toString())
        .replace(File.separatorChar, Package.CANONICAL_SEPARATOR)
        .removeSuffix(".class")

val Path.isClass get() = name.endsWith(".class")

fun Class<*>.asArray(): Class<*> = Array.newInstance(this, 0).javaClass

fun ExecutionDataStore.deepCopy(): ExecutionDataStore {
    val executionDataCopy = ExecutionDataStore()
    for (content in contents) {
        val ed = ExecutionData(content.id, content.name, content.probes.copyOf())
        executionDataCopy.put(ed)
    }
    return executionDataCopy
}

class JacocoCoverageProvider : CoverageProvider {
    private val tgaTempDir = Files.createTempDirectory("tga").also {
        deleteOnExit(it)
    }
    private val compiledDir: Path = Files.createTempDirectory(tgaTempDir, "compiled").also {
        deleteOnExit(it)
    }

    override fun computeCoverage(benchmark: Benchmark, testSuite: TestSuite): ClassCoverageInfo {
        val allTests = Files.walk(testSuite.testSrcPath).filter { it.isClass }.toList()
        val classPath = benchmark.classPath + testSuite.dependencies
        val compiler = SystemJavaCompiler(classPath)
        compiler.compile(allTests, compiledDir)

        val runtime = LoggerRuntime()
        val classLoader = InstrumentingPathClassLoader(classPath, setOf(benchmark.klass), runtime)


        val datum = mutableMapOf<Path, ExecutionDataStore>()
        val data = RuntimeData()
        runtime.startup(data)

        for (testPath in allTests) {
            val testClassName = testPath.fullyQualifiedName(compiledDir)
            val testClass = classLoader.loadClass(testClassName)
            log.debug("Running test $testClassName")

            val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")
            val jc = jcClass.newInstance()
            val computerClass = classLoader.loadClass("org.junit.runner.Computer")
            jcClass.getMethod("run", computerClass, Class::class.java.asArray())
                .invoke(jc, computerClass.newInstance(), arrayOf(testClass))

            val executionData = ExecutionDataStore()
            data.collect(executionData, SessionInfoStore(), false)
            datum[testPath] = executionData.deepCopy()
            data.reset()
        }

        runtime.shutdown()

        val mergedExecutionData = ExecutionDataStore()
        for (testPath in allTests) {
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
                    val line = method.getLine(lineNumber - method.firstLine)

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
                    method.name,
                    method.desc,
                    CoverageInfo(instructions),
                    CoverageInfo(lines),
                    CoverageInfo(branches)
                )
            }
        }.first()

        return ClassCoverageInfo(benchmark.klass, methods)
    }

    class InstrumentingPathClassLoader(
        val paths: List<Path>,
        val targets: Set<String>,
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

//    val allTestClasses: Set<Path> by lazy { Files.walk(compileDir).filter { it.isClass }.toList().toSet() }
//    protected val compileDir = kexConfig.compiledCodeDirectory
//    protected lateinit var coverageContext: CoverageContext
//    protected lateinit var executionData: Map<Path, ExecutionDataStore>
//
//    init {
//        for (container in containers) {
//            container.extract(jacocoInstrumentedDir)
//        }
//    }
//
//
//    open fun computeCoverage(
//        analysisLevel: AnalysisLevel,
//        testClasses: Set<Path> = this.allTestClasses
//    ): CommonCoverageInfo {
//        ktassert(this.allTestClasses.containsAll(testClasses)) {
//            log.error("Unexpected set of test classes")
//        }
//
//        val classes = scanTargetClasses(analysisLevel)
//        val coverageBuilder = getCoverageBuilderAndCleanup(classes, testClasses)
//
//        return when (analysisLevel) {
//            is PackageLevel -> getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
//            is ClassLevel -> getClassCoverage(cm, coverageBuilder).first()
//            is MethodLevel -> getMethodCoverage(coverageBuilder, analysisLevel.method)!!
//        }
//    }
//
//    open fun computeCoverageSaturation(
//        analysisLevel: AnalysisLevel,
//        testClasses: Set<Path> = this.allTestClasses
//    ): SortedMap<Duration, CommonCoverageInfo> {
//        ktassert(this.allTestClasses.containsAll(testClasses)) {
//            log.error("Unexpected set of test classes")
//        }
//
//        val batchedTestClasses = testClasses.batchByTime()
//        val maxTime = batchedTestClasses.lastKey()
//
//        val classes = scanTargetClasses(analysisLevel)
//        val coverageContext = buildCoverageContext(classes)
//        val executionData = computeExecutionData(coverageContext, testClasses)
//
//        val coverageSaturation = batchedTestClasses.mapValuesTo(sortedMapOf()) { (duration, batchedTests) ->
//            log.debug("Running tests for batch {} / {}", duration.inWholeSeconds, maxTime.inWholeSeconds)
//            val coverageBuilder = buildCoverageBuilder(coverageContext, batchedTests, executionData)
//            when (analysisLevel) {
//                is PackageLevel -> getPackageCoverage(analysisLevel.pkg, cm, coverageBuilder)
//                is ClassLevel -> getClassCoverage(cm, coverageBuilder).first()
//                is MethodLevel -> getMethodCoverage(coverageBuilder, analysisLevel.method)!!
//            }
//        }
//
//        cleanupCoverageContext(coverageContext)
//        return coverageSaturation
//    }
//
//    protected fun scanTargetClasses(analysisLevel: AnalysisLevel): List<Path> = when (analysisLevel) {
//        is PackageLevel -> Files.walk(jacocoInstrumentedDir)
//            .filter { it.isClass }
//            .filter { analysisLevel.pkg.isParent(it.fullyQualifiedName(jacocoInstrumentedDir).asmString) }
//            .toList()
//
//        is ClassLevel -> {
//            val klass = analysisLevel.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
//            listOf(jacocoInstrumentedDir.resolve("$klass.class"))
//        }
//
//        is MethodLevel -> {
//            val method = analysisLevel.method
//            val klass = method.klass.fullName.replace(Package.SEPARATOR, File.separatorChar)
//            listOf(jacocoInstrumentedDir.resolve("$klass.class"))
//        }
//    }
//
//    protected fun initializeContext(classes: List<Path>) {
//        if (!this::coverageContext.isInitialized) {
//            coverageContext = buildCoverageContext(classes)
//            executionData = computeExecutionData(coverageContext, allTestClasses)
//            cleanupCoverageContext(coverageContext)
//        }
//    }
//
//    protected fun getCoverageBuilderAndCleanup(
//        classes: List<Path>,
//        testClasses: Set<Path>
//    ): CoverageBuilder {
//        initializeContext(classes)
//        val coverageBuilder = buildCoverageBuilder(coverageContext, testClasses, executionData)
//        return coverageBuilder
//    }
//
//    class CoverageContext(
//        val runtime: LoggerRuntime,
//        val classes: List<Path>,
//        val originalClassBytecode: Map<Path, ByteArray>
//    )
//
//    protected fun buildCoverageContext(
//        classes: List<Path>
//    ): CoverageContext {
//        val runtime = LoggerRuntime()
//        val originalClasses = mutableMapOf<Path, ByteArray>()
//        for (classPath in classes) {
//            originalClasses[classPath] = classPath.readBytes()
//            val instrumented = classPath.inputStream().use {
//                val fullyQualifiedName = classPath.fullyQualifiedName(jacocoInstrumentedDir)
//                val instr = Instrumenter(runtime)
//                instr.instrument(it, fullyQualifiedName)
//            }
//            classPath.writeBytes(instrumented)
//        }
//        return CoverageContext(runtime, classes, originalClasses)
//    }
//
//    protected fun buildCoverageBuilder(
//        context: CoverageContext,
//        testClasses: Set<Path>,
//        executionData: Map<Path, ExecutionDataStore>,
//        logProgress: Boolean = true
//    ): CoverageBuilder {
//        val mergedExecutionData = ExecutionDataStore()
//        for (testPath in testClasses) {
//            val executions = executionData[testPath] ?: continue
//            for (data in executions.contents) {
//                mergedExecutionData.put(data)
//            }
//        }
//
//        if (logProgress) log.debug("Constructing coverage builder for: {}", testClasses)
//        val coverageBuilder = CoverageBuilder()
//        val analyzer = Analyzer(mergedExecutionData, coverageBuilder)
//        for (className in context.classes) {
//            context.originalClassBytecode[className]?.inputStream()?.use {
//                tryOrNull {
//                    analyzer.analyzeClass(it, className.fullyQualifiedName(jacocoInstrumentedDir))
//                }
//            }
//        }
//        return coverageBuilder
//    }
//
//    protected fun computeExecutionData(
//        context: CoverageContext,
//        testClasses: Set<Path>,
//        logProgress: Boolean = true
//    ): Map<Path, ExecutionDataStore> {
//        val datum = mutableMapOf<Path, ExecutionDataStore>()
//        val data = RuntimeData()
//        context.runtime.startup(data)
//
//        if (logProgress) log.debug("Running tests...")
//        val classLoader = PathClassLoader(listOf(jacocoInstrumentedDir, compileDir))
//        for (testPath in testClasses) {
//            val testClassName = testPath.fullyQualifiedName(compileDir)
//            val testClass = classLoader.loadClass(testClassName)
//            if (logProgress) log.debug("Running test $testClassName")
//
//            val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")
//            val jc = jcClass.newInstance()
//            val computerClass = classLoader.loadClass("org.junit.runner.Computer")
//            jcClass.getMethod("run", computerClass, Class::class.java.asArray())
//                .invoke(jc, computerClass.newInstance(), arrayOf(testClass))
//
//            val executionData = ExecutionDataStore()
//            data.collect(executionData, SessionInfoStore(), false)
//            datum[testPath] = executionData.deepCopy()
//            data.reset()
//        }
//
//        context.runtime.shutdown()
//        return datum
//    }
//
//    protected fun ExecutionDataStore.deepCopy(): ExecutionDataStore {
//        val executionDataCopy = ExecutionDataStore()
//        for (content in contents) {
//            val ed = ExecutionData(content.id, content.name, content.probes.copyOf())
//            executionDataCopy.put(ed)
//        }
//        return executionDataCopy
//    }
//
//    protected fun cleanupCoverageContext(context: CoverageContext) {
//        for (className in context.classes) {
//            val bytecode = context.originalClassBytecode[className] ?: continue
//            className.writeBytes(bytecode)
//        }
//    }
//
//    protected val String.fullyQualifiedName: String
//        get() = removeSuffix(".class").javaString
//
//    protected fun Path.fullyQualifiedName(base: Path): String =
//        relativeTo(base).toString()
//            .removePrefix("..")
//            .removePrefix(File.separatorChar.toString())
//            .replace(File.separatorChar, Package.CANONICAL_SEPARATOR)
//            .removeSuffix(".class")
//
//    protected fun getClassCoverage(
//        cm: ClassManager,
//        coverageBuilder: CoverageBuilder
//    ): Set<ClassCoverageInfo> =
//        coverageBuilder.classes.mapTo(mutableSetOf()) {
//            val kfgClass = cm[it.name]
//            val classCov = getCommonCounters<ClassCoverageInfo>(it.name, it)
//            for (mc in it.methods) {
//                val kfgMethod = kfgClass.getMethod(mc.name, mc.desc)
//                classCov.methods += getCommonCounters<MethodCoverageInfo>(kfgMethod.prototype.fullyQualifiedName, mc)
//            }
//            classCov
//        }
//
//    protected fun getMethodCoverage(coverageBuilder: CoverageBuilder, method: Method): CommonCoverageInfo? {
//        for (mc in coverageBuilder.classes.iterator().next().methods) {
//            if (mc.name == method.name && mc.desc == method.asmDesc) {
//                return getCommonCounters<MethodCoverageInfo>(method.prototype.fullyQualifiedName, mc)
//            }
//        }
//        return null
//    }
//
//    protected fun getPackageCoverage(
//        pkg: Package,
//        cm: ClassManager,
//        coverageBuilder: CoverageBuilder
//    ): PackageCoverageInfo {
//        val pc = PackageCoverageImpl(pkg.canonicalName, coverageBuilder.classes, coverageBuilder.sourceFiles)
//        val packCov = getCommonCounters<PackageCoverageInfo>(pkg.canonicalName, pc)
//        packCov.classes.addAll(getClassCoverage(cm, coverageBuilder))
//        return packCov
//    }
//
//    protected fun getCounter(unit: CoverageUnit, counter: ICounter): CoverageInfo {
//        val covered = counter.coveredCount
//        val total = counter.totalCount
//        return GenericCoverageInfo(covered, total, unit)
//    }
//
//    protected inline fun <reified T : CommonCoverageInfo> getCommonCounters(name: String, coverage: ICoverageNode): T {
//        val insts = getCounter(CoverageUnit.INSTRUCTION, coverage.instructionCounter)
//        val brs = getCounter(CoverageUnit.BRANCH, coverage.branchCounter)
//        val lines = getCounter(CoverageUnit.LINE, coverage.lineCounter)
//        val complexities = getCounter(CoverageUnit.COMPLEXITY, coverage.complexityCounter)
//
//        return when (T::class.java) {
//            MethodCoverageInfo::class.java -> MethodCoverageInfo(name, insts, brs, lines, complexities)
//            ClassCoverageInfo::class.java -> ClassCoverageInfo(name, insts, brs, lines, complexities)
//            PackageCoverageInfo::class.java -> PackageCoverageInfo(name, insts, brs, lines, complexities)
//            else -> unreachable { log.error("Unknown common coverage info class ${T::class.java}") }
//        } as T
//    }
}
