plugins {
    id("org.plan.research.tga-pipeline-base")
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    api(project(":tga-core"))
}


task<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.plan.research.tga.runner.MainKt")
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

tasks {
    build {
        dependsOn(runnerTask)
    }
}
