package org.plan.research.tga.tool.kex

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
import kotlin.time.Duration

class KexCmdTool : TestGenerationTool {
    override val name = "kex"
    private lateinit var outputDirectory: Path

    private val kexHome = Paths.get(System.getenv("KEX_HOME"))
        ?: unreachable { log.error("No KEX_HOME environment variable") }

    private lateinit var classPath: List<Path>
    override fun init(src: Path, classPath: List<Path>) {
        this.classPath = classPath
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.outputDirectory = outputDirectory
        var process: Process? = null
        try {
            val kexProcessBuilder = ProcessBuilder(
                "python3",
                "${kexHome.resolve("kex.py")}",
                "--classpath", classPath.joinToString(File.pathSeparator!!),
                "--target", target,
                "--mode", "concolic",
                "--output", outputDirectory.toString(),
                "--option", "concolic:timeLimit:${timeLimit.inWholeSeconds}",
                "--option", "kex:computeCoverage:false",
            )
            log.debug("Starting Kex with command: {}", kexProcessBuilder.command())

            process = kexProcessBuilder.start()!!
            process.waitFor()
        } catch (e: InterruptedException) {
            log.error("Kex was interrupted on target $target")
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
                kexHome.resolve("runtime-deps", "lib", "junit-4.13.2.jar"),
                kexHome.resolve("runtime-deps", "lib", "hamcrest-core-1.3.jar")
            )
        )
    }

}
