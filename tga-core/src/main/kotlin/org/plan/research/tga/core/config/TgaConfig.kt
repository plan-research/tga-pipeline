package org.plan.research.tga.core.config

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.vorpal.research.kthelper.assert.exit
import java.io.PrintWriter
import java.io.StringWriter

fun buildOptions(builderAction: Options.() -> Unit): Options {
    val options = Options()
    options.builderAction()
    return options
}

abstract class TgaConfig(val options: Options, args: Array<String>) {
    private val cmd: CommandLine

    init {
        val parser = DefaultParser()

        cmd = try {
            parser.parse(options, args)
        } catch (e: ParseException) {
            exit<CommandLine> {
                System.err.println("Error parsing command line arguments: ${e.message}")
                printHelp()
            }
        }

        getCmdValue("help")?.let {
            exit {
                printHelp()
            }
        }
    }

    fun getCmdValue(name: String): String? = cmd.getOptionValue(name)
    fun getCmdValue(name: String, default: String) = getCmdValue(name) ?: default

    fun printHelp() {
        println(helpString)
    }

    private val helpString: String
        get() {
            val helpFormatter = HelpFormatter()
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            helpFormatter.printHelp(pw, 80, "org/plan/research/tga/runner", null, options, 1, 3, null)
            return sw.toString()
        }
}

