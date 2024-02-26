package org.plan.research.tga

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.plan.research.tga.benchmark.Benchmark
import org.vorpal.research.kthelper.resolve
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isDirectory

private val json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = false
    prettyPrint = true
    useArrayPolymorphism = false
    allowStructuredMapKeys = true
    allowSpecialFloatingPointValues = true
}

fun main(args: Array<String>) {
    val benchmarkDir = Paths.get(args[0])

    val benchmarks = buildList {
        for (project in Files.walk(benchmarkDir, 1).skip(1).filter { it.isDirectory() }) {
            println(project)

            val name = project.fileName.toString().substringAfterLast('_')
            println(name)
            val srcPath = project.resolve("src", "main", "java")
            val classPath = buildList {
                add(project.resolve("${name}.jar"))
                addAll(
                    Files
                        .walk(project.resolve("lib"))
                        .filter { it.fileName.toString().endsWith(".jar") }
                        .toList()
                )
            }
            for (klass in Files.walk(srcPath).filter { it.fileName.toString().endsWith(".java") }) {
                val klassName = srcPath.relativize(klass).toString().substringBeforeLast(".java").replace('/', '.')
                val benchmark = Benchmark(name, srcPath, classPath, klassName)
                add(benchmark)
            }
        }
    }
    File("sf110-full.json").writeText(
        json.encodeToString(benchmarks)
    )
}
