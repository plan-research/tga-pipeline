package org.plan.research.tga.analysis.junit

import kotlinx.serialization.Serializable

private fun String.splitAtLast(char: Char): Pair<String, String> {
    val split = this.lastIndexOf(char)
    if (split < 0) return this to ""
    return substring(0, split) to substring(split + 1, length)
}

@Serializable
data class StackTraceLine(
    val klassName: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int
)

@Serializable
data class StackTrace(
    val firstLine: String,
    val stackTraceLines: List<StackTraceLine>
) {
    val size: Int get() = stackTraceLines.size

    companion object {
        fun parse(text: String): StackTrace {
            val lines = text.split(System.lineSeparator()).filter { it.isNotBlank() }.takeWhile { !it.startsWith("Caused by:") }
            val firstLine = lines.first()
            val stackTraceLines = mutableListOf<StackTraceLine>()
            for (line in lines.drop(1).dropWhile { !it.contains("^\\s*at ".toRegex()) }) {
                try {
                    val codeElement = line.trim().replace("\\s*at\\s+".toRegex(), "")
                        .substringBefore(' ').trim()
                    val (klassAndMethod, location) = codeElement.split('(')
                    val (klassName, methodName) = klassAndMethod.splitAtLast('.')
                    val (fileName, lineNumber) = when {
                        line.endsWith("(Native Method)") -> null to "-2"
                        line.endsWith("(Unknown Source)") -> null to "-1"
                        ':' in location -> location.dropLast(1).splitAtLast(':')
                        else -> location.dropLast(1) to "-1"
                    }
                    stackTraceLines += StackTraceLine(klassName, methodName, fileName, lineNumber.toInt())
                } catch (e: Throwable) {
                    throw e
                }
            }
            return StackTrace(firstLine, stackTraceLines)
        }
    }


    val originalStackTrace: String
        get() = buildString {
            appendLine(firstLine)
            for (line in stackTraceLines)
                appendLine("\tat $line")
        }

    val throwable get() = firstLine.takeWhile { it != ':' }

    infix fun `in`(other: StackTrace): Boolean {
        if (this.throwable != other.throwable) return false
        var thisIndex = 0
        var otherIndex = 0
        while (otherIndex < other.stackTraceLines.size) {
            val thisLine = this.stackTraceLines[thisIndex]
            val otherLine = other.stackTraceLines[otherIndex]

            if (thisLine == otherLine) {
                ++thisIndex
                ++otherIndex
                if (thisIndex == this.stackTraceLines.size) return true
            } else if (thisIndex > 0) {
                thisIndex = 0
            } else {
                ++otherIndex
            }
        }
        return false
    }
}
