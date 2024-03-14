package org.plan.research.tga.compiler

import org.vorpal.research.kthelper.KtException
import java.nio.file.Path

class CompilationException : KtException {
    constructor(message: String = "") : super(message)

    @Suppress("unused")
    constructor(message: String = "", throwable: Throwable) : super(message, throwable)
}

interface JavaCompiler {
    val classPath: List<Path>

    fun compile(sources: List<Path>, outputDirectory: Path): List<Path>
}
