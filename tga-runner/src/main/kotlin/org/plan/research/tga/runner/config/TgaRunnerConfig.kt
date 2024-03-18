package org.plan.research.tga.runner.config

import org.apache.commons.cli.Option
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions


class TgaRunnerConfig(args: Array<String>) : TgaConfig(options, args) {
    companion object {
        private val options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option("c", "config", true, "configuration file")
                    .also { it.isRequired = true }
            )
        }
    }
}
