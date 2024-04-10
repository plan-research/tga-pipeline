package org.plan.research.tga.core.dependency

import java.nio.file.Path
import java.nio.file.Paths

class DependencyManager {
    private val loadedDependencies = preloadedDependencies.toMutableMap()

    companion object {
        private val preloadedDependencies = mutableMapOf(
            Dependency("org.junit", "junit", "4.13.2") to listOf(
                Paths.get("libs/hamcrest-core-1.3.jar"),
                Paths.get("libs/junit-4.13.2.jar"),
            )
        )
    }

    fun findDependency(dependency: Dependency): List<Path> = loadedDependencies.getOrPut(dependency) {
        TODO()
    }
}
