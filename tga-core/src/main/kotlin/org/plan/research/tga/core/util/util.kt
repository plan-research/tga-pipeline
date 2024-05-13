package org.plan.research.tga.core.util

import org.slf4j.MDC
import java.nio.file.Path
import java.nio.file.Paths

val TGA_PIPELINE_HOME: Path = Paths.get(System.getProperty("tga.pipeline.home"))


fun initLog(outputDirectory: Path, filename: String) {
    MDC.put("kex-run-id", outputDirectory.resolve(filename).toAbsolutePath().toString())
}
