plugins {
    id("org.plan.research.tga-pipeline-base")
    application
}

dependencies {
    api(project(":tga-core"))
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
}


application {
    mainClass.set("org.plan.research.tga.runner.MainKt")
}
