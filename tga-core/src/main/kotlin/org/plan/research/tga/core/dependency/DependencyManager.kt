package org.plan.research.tga.core.dependency

import dev.jeka.core.api.depmanagement.JkDependencySet
import dev.jeka.core.api.depmanagement.JkRepo
import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver
import dev.jeka.core.api.depmanagement.resolution.JkResolutionParameters
import java.nio.file.Path

class DependencyManager {
    private val lock = Any()
    private val loadedDependencies = mutableMapOf<Dependency, List<Path>>()

    fun findDependency(dependency: Dependency): List<Path> = synchronized(lock) {
        loadedDependencies.getOrPut(dependency) {
            val deps = JkDependencySet.of()
                .and("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
            val resolver = JkDependencyResolver.of(JkRepo.ofMavenCentral());
            resolver.resolve(deps, JkResolutionParameters.of()).files.entries
        }
    }
}
