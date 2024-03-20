package org.plan.research.tga.tool.stub

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.time.Duration

class StubTool : TestGenerationTool {
    override val name = "stub"

    override fun init(src: Path, classPath: List<Path>) {
        log.debug("Initialized stub with {}, class path {}", src, classPath)
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path): TestSuite {
        log.debug(
            "Initialized stub with target {}, time limit {}, output directory {}",
            target,
            timeLimit,
            outputDirectory
        )

        val testDir = outputDirectory.resolve("tests").also {
            it.toFile().mkdirs()
        }

        return TestSuite(testDir, emptyList(), emptyList())
    }
}
