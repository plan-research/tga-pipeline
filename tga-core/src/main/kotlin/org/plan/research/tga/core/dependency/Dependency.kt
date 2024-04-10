package org.plan.research.tga.core.dependency

import kotlinx.serialization.Serializable

@Serializable
data class Dependency(
    val groupId: String,
    val artifactId: String,
    val version: String
)
