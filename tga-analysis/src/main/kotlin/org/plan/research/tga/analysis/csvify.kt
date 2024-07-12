package org.plan.research.tga.analysis

import org.vorpal.research.kthelper.toBoolean
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter
import kotlin.io.path.forEachLine

fun main(args: Array<String>) {
    val input = Paths.get(args[0])

    val header = "tool,runName,iteration,benchmark buildId,benchmark klass,compilation rate,line coverage," +
            "branch coverage,mutation score,package,internal dependencies,stdlib dependencies,external dependencies," +
            "language,comments,java docs,sloc,total dependencies"

    val modelName = "gpt4o"

    Paths.get(input.fileName.toString().removeSuffix(".log") + "-$modelName.csv").bufferedWriter().use { writer ->
        writer.appendLine(header)
        input.forEachLine { line ->
            if ("[DEBUG][org.plan.research.tga.analysis.MainKt" in line) return@forEachLine
            if ("->" !in line) return@forEachLine

            val values = line.split(',').map { if ("->" in it) it.split("->")[1].trim() else it }.map { it.trim() }
                .map { if (it == "NaN") "0.0" else it }.toMutableList()
            values[14] = values[14].toInt().toBoolean().toString()
            values[15] = values[15].toInt().toBoolean().toString()
            if (values[1].split("-")[1] != modelName) return@forEachLine
            values.add((values[10].toInt() + values[11].toInt() + values[12].toInt()).toString())
            writer.appendLine(values.joinToString(separator = ","))
        }
    }
}
