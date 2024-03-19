package org.plan.research.tga.runner.compiler

import org.plan.research.tga.core.compiler.CompilationException
import org.plan.research.tga.core.compiler.JavaCompiler
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

class SystemJavaCompiler(
    override val classPath: List<Path>,
) : JavaCompiler {
    private val compiler = ToolProvider.getSystemJavaCompiler()

    override fun compile(sources: List<Path>, outputDirectory: Path): List<Path> {
        if (sources.isEmpty()) {
            log.error("Trying to compile empty list of sources, skipping compilation")
            return emptyList()
        }
        log.debug("Compiling {} into {}", sources, outputDirectory)
        val fileManager = compiler.getStandardFileManager(null, null, null)
        fileManager.setLocation(StandardLocation.CLASS_PATH, classPath.map { it.toFile() })
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(outputDirectory.toFile()))
        val objects = fileManager.getJavaFileObjectsFromFiles(sources.map { it.toFile() })

        val compilerOutput = ByteArrayOutputStream()
        val task = compiler.getTask(
            compilerOutput.writer(),
            fileManager,
            null,
            listOf("-Xlint:none", "-Xlint:unchecked"),
            null,
            objects
        )
        val compileSuccess = `try` { task.call() }.getOrElse { false }
        if (!compileSuccess) {
            log.error("Task $task failed")
            log.error("Sources: ${sources.joinToString("\n", prefix = "\n")}")
            log.error(compilerOutput.toString())
            throw CompilationException()
        }

        return fileManager.list(
            StandardLocation.CLASS_OUTPUT,
            "", Collections.singleton(JavaFileObject.Kind.CLASS), true
        ).map { Paths.get(it.name).toAbsolutePath() }
    }
}
