package org.plan.research.tga.tool.manual

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.time.Duration
import org.plan.research.tga.core.dependency.Dependency
import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.logging.log

class ManualTests : TestGenerationTool {
    override val name: String = "Manual"
    lateinit var manualTestsPath: Path
    lateinit var outputDirectory: Path

    override fun init(root: Path, classPath: List<Path>) {
        manualTestsPath = root
        for (dir in listOf("src", "test", "java")) {
            val next = manualTestsPath.resolve(dir)
            if (next.exists()) {
                manualTestsPath = next
            }
        }
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        this.outputDirectory = outputDirectory
        log.debug("Manual tests do not do anything")
    }

    override fun report(): TestSuite {
        outputDirectory.toFile().mkdirs()
        val tests = mutableListOf<String>()
        if (manualTestsPath.exists()) {
            Files.walk(manualTestsPath).use { stream ->
                stream.forEach { sourcePath ->
                    val targetPath = outputDirectory.resolve(manualTestsPath.relativize(sourcePath))
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath)
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                        tests += outputDirectory.relativize(targetPath).toString().replace('/', '.').removeSuffix(".java")
                    }
                }
            }
        }
        return TestSuite(
            outputDirectory,
            tests,
            emptyList(),
            listOf(
                Dependency("org.junit.jupiter", "junit-jupiter-api", "5.12.0"),
                Dependency("junit", "junit", "4.13.2"),
                Dependency("org.junit.vintage", "junit-vintage-engine", "5.12.0"),
            ),
        )
    }
}
