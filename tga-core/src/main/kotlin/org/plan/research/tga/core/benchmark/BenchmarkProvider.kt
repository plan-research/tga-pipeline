package org.plan.research.tga.core.benchmark

interface BenchmarkProvider {
    fun benchmarks(): Collection<Benchmark>
}
