package org.plan.research.tga.tool.config

import org.apache.commons.cli.Option
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions

class TgaToolConfig(args: Array<String>) : TgaConfig("tga-tool", options, args) {
    companion object {
        private val options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option("i", "ip", true, "tga server address")
                    .also { it.isRequired = true }
            )

            addOption(
                Option("p", "port", true, "tga server port")
                    .also { it.isRequired = true }
            )

            addOption(
                Option("t", "tool", true, "tool name")
                    .also { it.isRequired = true }
            )

            addOption(
                Option(null, "toolArgs", true, "additional tool arguments")
                    .also { it.isRequired = false }
            )
        }
    }
}
