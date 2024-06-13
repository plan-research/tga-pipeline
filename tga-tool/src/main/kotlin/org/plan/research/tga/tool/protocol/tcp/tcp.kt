package org.plan.research.tga.tool.protocol.tcp

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.tool.protocol.GenerationRequest
import org.plan.research.tga.core.tool.protocol.GenerationResult
import org.plan.research.tga.core.tool.protocol.Tool2TgaConnection
import org.plan.research.tga.core.tool.protocol.protocolJson
import org.vorpal.research.kthelper.logging.log
import java.net.Socket


class TcpTool2TgaConnection(
    host: String,
    port: UInt
) : Tool2TgaConnection {
    private val socket = Socket(host, port.toInt())
    private val reader = socket.getInputStream().bufferedReader()
    private val writer = socket.getOutputStream().bufferedWriter()

    override fun init(name: String) {
        log.debug("Initializing tool $name")
        writer.write(name)
        writer.write("\n")
        writer.flush()
    }

    override fun receive(): GenerationRequest {
        val json = reader.readLine()
        log.debug("Received request from server: $json")
        return protocolJson.decodeFromString(json)
    }

    override fun send(result: GenerationResult) {
        val json = protocolJson.encodeToString(result)
        log.debug("Sending result to server: $json")
        writer.write(json)
        writer.write("\n")
        writer.flush()
    }

    override fun close() {
        reader.close()
        writer.close()
        socket.close()
    }
}
