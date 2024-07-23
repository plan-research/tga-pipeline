@file:Suppress("UNUSED_VARIABLE")

package org.plan.research.tga.analysis.junit

import org.plan.research.tga.analysis.compilation.CompilationResult
import org.plan.research.tga.core.util.asArray
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.logging.log
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader

class JUnitRunner {
    fun run(compilationResult: CompilationResult) = buildSet {
        for ((testName, _) in compilationResult.compilableTests) {
            addAll(run(compilationResult, testName))
        }
    }

    fun run(compilationResult: CompilationResult, testName: String): Set<StackTrace> = try {
        val classLoader = URLClassLoader(compilationResult.fullClassPath.mapToArray { it.toUri().toURL() })
        val testClass = classLoader.loadClass(testName)
        val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")

        @Suppress("DEPRECATION")
        val jc = jcClass.newInstance()
        val computerClass = classLoader.loadClass("org.junit.runner.Computer")

        @Suppress("DEPRECATION")
        val returnValue = jcClass.getMethod("run", computerClass, Class::class.java.asArray())
            .invoke(jc, computerClass.newInstance(), arrayOf(testClass))

        val resultClass = classLoader.loadClass("org.junit.runner.Result")
        val failureClass = classLoader.loadClass("org.junit.runner.notification.Failure")
        val throwableField = failureClass.getDeclaredField("fThrownException").also {
            it.isAccessible = true
        }
        (resultClass.getDeclaredField("failures")
            .also { it.isAccessible = true }
            .get(returnValue) as List<*>)
            .mapNotNull { throwableField.get(it) as? Throwable? }
            .mapTo(mutableSetOf()) {
                val w = StringWriter()
                it.printStackTrace(PrintWriter(w))
                StackTrace.parse(w.toString())
            }
    } catch (e: Throwable) {
        log.error("Error when executing test $testName, ", e)
        emptySet()
    }
}
