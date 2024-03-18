plugins {
    id("org.plan.research.tga-pipeline-base")
    kotlin("plugin.serialization") version "1.9.20"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}
