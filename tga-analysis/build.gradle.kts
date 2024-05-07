plugins {
    id("org.plan.research.tga-pipeline-base")
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    api(project(":tga-core"))
}

task<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.plan.research.tga.analysis.MainKt")
}

