package org.plan.research.tga.tool

import java.nio.file.Path

interface TestGenerationTool {
    fun init(
        src: Path,
        classPath: List<Path>,
    )

    fun run(): TestSuite
}
