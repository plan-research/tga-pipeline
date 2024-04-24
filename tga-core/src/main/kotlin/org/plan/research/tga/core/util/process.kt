package org.plan.research.tga.core.util


fun Process.destroyRecursively() {
    this.toHandle().destroyRecursively()
}

fun ProcessHandle.destroyRecursively() {
    this.children().forEach {
        it.destroyRecursively()
    }
    this.destroy()
    if (this.isAlive) {
        this.destroyForcibly()
    }
}
