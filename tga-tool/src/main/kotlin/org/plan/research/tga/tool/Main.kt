package org.plan.research.tga.tool

import org.plan.research.tga.core.tool.TestGenerationTool
import org.plan.research.tga.core.tool.protocol.Tool2TgaConnection
import org.plan.research.tga.tool.config.TgaToolConfig
import org.plan.research.tga.tool.protocol.tcp.TcpTool2TgaConnection
import org.plan.research.tga.tool.stub.StubTool
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

fun main(args: Array<String>) {
    val config = TgaToolConfig(args)


    val serverAddress = config.getCmdValue("ip")!!
    val serverPort = config.getCmdValue("port")!!.toUInt()
    val connection = run {
        var connection: Tool2TgaConnection?
        do {
            connection = try {
                TcpTool2TgaConnection(serverAddress, serverPort)
            } catch (e: Throwable) {
                log.error("Could not connect to server, attempting again in 1 second: ", e)
                Thread.sleep(1_000)
                null
            }
        } while (connection == null)
        connection
    }

    val tool: () -> TestGenerationTool = when (val name = config.getCmdValue("tool")!!) {
        "stub" -> {
            { StubTool() }
        }

        else -> unreachable { log.error("Unknown tool name: $name") }
    }

    val controller = ToolController(connection, tool)
    controller.run()
}
