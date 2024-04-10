package org.plan.research.tga.runner.config

import org.apache.commons.cli.Option
import org.plan.research.tga.core.config.TgaConfig
import org.plan.research.tga.core.config.buildOptions


class TgaRunnerConfig(args: Array<String>) : TgaConfig("tga-runner", options, args) {
    companion object {
        private val options = buildOptions {
            addOption(
                Option("h", "help", false, "print this help and quit").also {
                    it.isRequired = false
                }
            )

            addOption(
                Option("p", "port", true, "server port to run on")
                    .also { it.isRequired = true }
            )

            addOption(
                Option("c", "config", true, "configuration file")
                    .also { it.isRequired = true }
            )

            addOption(
                Option("t", "timeout", true, "time limit for test generation, in seconds")
                    .also { it.isRequired = true }
            )

            addOption(
                Option("o", "output", true, "output directory")
                    .also { it.isRequired = true }
            )

            addOption(
                Option("n", "runs", true, "number of runs")
                    .also { it.isRequired = true }
            )
        }
    }
}
