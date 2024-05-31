package org.plan.research.tga.tool.stub

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.TestSuite
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Path
import kotlin.time.Duration

class StubTool : TestGenerationTool {
    override val name = "stub"
    private lateinit var testDir: Path

    override fun init(root: Path, classPath: List<Path>) {
        log.debug("Initialized stub with {}, class path {}", root, classPath)
    }

    override fun run(target: String, timeLimit: Duration, outputDirectory: Path) {
        log.debug(
            "Initialized stub with target {}, time limit {}, output directory {}",
            target,
            timeLimit,
            outputDirectory
        )

        testDir = outputDirectory.resolve("tests").also {
            it.toFile().mkdirs()
        }
    }

    override fun report(): TestSuite {
        return TestSuite(testDir, emptyList(), emptyList(), emptyList())
    }
}
