import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val properties = loadProperties(rootDir.parentFile.resolve("gradle.properties").absolutePath)

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${properties["kotlin.version"]}")
    implementation("de.undercouch.download:de.undercouch.download.gradle.plugin:5.1.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
