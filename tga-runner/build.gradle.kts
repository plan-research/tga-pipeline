plugins {
    application
    id("org.plan.research.tga-pipeline-base")
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    api(project(":tga-core"))
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
}


application {
    mainClass.set("org.plan.research.tga.runner.MainKt")
}
