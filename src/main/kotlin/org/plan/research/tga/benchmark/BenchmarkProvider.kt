package org.plan.research.tga.benchmark

interface BenchmarkProvider {
    fun benchmarks(): Collection<Benchmark>
}
