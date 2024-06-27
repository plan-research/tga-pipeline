plugins {
    id("org.plan.research.tga-pipeline-base")
}

dependencies {
    api(project(":tga-core"))
}


task<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.plan.research.tga.tool.MainKt")
}

val toolTask = task<Jar>("toolJar") {
    manifest {
        attributes["Main-Class"] = "org.plan.research.tga.tool.MainKt"
    }
    archiveFileName.set("tga-tool.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks {
    build {
        dependsOn(toolTask)
    }
}
