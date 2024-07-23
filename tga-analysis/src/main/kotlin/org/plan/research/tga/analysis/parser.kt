package org.plan.research.tga.analysis

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.stmt.TryStmt
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter

fun main(args: Array<String>) {
    val resultsDir = Paths.get(args[0])

    Files.walk(resultsDir).forEach { file: Path ->
        if (file.toString().endsWith(".java")) {
            val compilationUnit = tryOrNull { StaticJavaParser.parse(file) } ?: return@forEach
            compilationUnit.findAll(ClassOrInterfaceDeclaration::class.java).forEach { classDeclaration ->
                classDeclaration.findAll(MethodDeclaration::class.java).forEach { methodDeclaration ->
                    methodDeclaration.findAll(TryStmt::class.java).forEach { tryStmt ->
                        when (val parent = tryStmt.parentNode.get()) {
                            is BlockStmt -> {
                                val index = parent.statements.indexOf(tryStmt)
                                parent.statements.removeAt(index)
                                for (subNode in tryStmt.tryBlock.childNodes.reversed()) {
                                    if (subNode is Statement) {
                                        parent.statements.add(index, subNode)
                                    } else if (subNode is LineComment) {
                                        continue
                                    } else {
                                        throw IllegalStateException("Unexpected element in try block: $subNode")
                                    }
                                }
                            }
                            else -> throw IllegalStateException("Unexpected parent: $parent")
                        }
                    }
                }
            }
            file.bufferedWriter().use {
                it.write(compilationUnit.toString())
            }
        }
    }
}
