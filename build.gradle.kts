plugins {
    application
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "org.plan.research"
version = "0.0.1"

// versions
val ktHelperVersion = "0.1.14"
val serializationVersion = "1.6.3"

val slf4jVersion = "2.0.12"
val logbackVersion = "1.5.0"
val commonsCliVersion = "1.5.0"

repositories {
    mavenCentral()
    maven("https://maven.apal-research.com")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.vorpal.research:kt-helper:$ktHelperVersion")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("commons-cli:commons-cli:$commonsCliVersion")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.plan.research.tga.MainKt")
}
