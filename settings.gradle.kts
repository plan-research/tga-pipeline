plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "tga-pipeline"

// versions
include("tga-core")
include("tga-tool")
include("tga-runner")
