import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "org.plan.research"
version = "0.0.38"

repositories {
    mavenCentral()
    maven(url = "https://maven.apal-research.com")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.vorpal.research:kt-helper:$ktHelperVersion")
    implementation("org.vorpal.research:kfg:$kfgVersion")
}

kotlin {
    jvmToolchain(11)
}

tasks.getByName<KotlinCompile>("compileKotlin") {
    kotlinOptions.allWarningsAsErrors = true
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", true)
}

tasks.withType<JavaExec> {
    systemProperties(
        "logback.statusListenerClass" to "ch.qos.logback.core.status.NopStatusListener",
        "tga.pipeline.home" to project.parent!!.projectDir.toPath().toAbsolutePath()
    )
}
