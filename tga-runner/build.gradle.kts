plugins {
    id("org.plan.research.tga-pipeline-base")
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    api(project(":tga-core"))
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
}


task<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.plan.research.tga.runner.MainKt")
}

task<JavaExec>("runCoverage") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.plan.research.tga.runner.coverage.jacoco.MainKt")
}
