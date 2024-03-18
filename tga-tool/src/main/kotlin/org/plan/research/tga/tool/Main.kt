package org.plan.research.tga.tool

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.tool.config.TgaToolConfig
import org.plan.research.tga.tool.protocol.tcp.TcpTool2TgaConnection
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

fun main(args: Array<String>) {
    val config = TgaToolConfig(args)


    val serverAddress = config.getCmdValue("ip")!!
    val serverPort = config.getCmdValue("port")!!.toUInt()
    val connection = TcpTool2TgaConnection(serverAddress, serverPort)

    val tool: () -> TestGenerationTool = when (val name = config.getCmdValue("tool")!!) {
        else -> unreachable { log.error("Unknown tool name: $name") }
    }

    ToolController(connection, tool)
}
