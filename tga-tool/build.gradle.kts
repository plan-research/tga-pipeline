plugins {
    id("org.plan.research.tga-pipeline-base")
    application
}

dependencies {
    api(project(":tga-core"))
}


application {
    mainClass.set("org.plan.research.tga.tool.MainKt")
}
