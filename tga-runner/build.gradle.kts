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

val runnerTask = task<Jar>("runnerJar") {
    manifest {
        attributes["Main-Class"] = "org.plan.research.tga.runner.MainKt"
    }
    archiveFileName.set("tga-runner.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

val coverageTask = task<Jar>("coverageJar") {
    manifest {
        attributes["Main-Class"] = "org.plan.research.tga.runner.coverage.jacoco.MainKt"
    }
    archiveFileName.set("tga-coverage.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks {
    build {
        dependsOn(runnerTask)
        dependsOn(coverageTask)
    }
}
