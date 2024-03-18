package org.plan.research.tga.tool.protocol.tcp

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.tool.protocol.BenchmarkRequest
import org.plan.research.tga.core.tool.protocol.GenerationResult
import org.plan.research.tga.core.tool.protocol.Tool2TgaConnection
import org.plan.research.tga.core.tool.protocol.protocolJson
import java.net.Socket


class TcpTool2TgaConnection(
    host: String,
    port: UInt
) : Tool2TgaConnection {
    private val socket = Socket(host, port.toInt())
    private val reader = socket.getInputStream().bufferedReader()
    private val writer = socket.getOutputStream().bufferedWriter()

    override fun receive(): BenchmarkRequest {
        val json = reader.readLine()
        return protocolJson.decodeFromString(json)
    }

    override fun send(result: GenerationResult) {
        writer.write(protocolJson.encodeToString(result))
        writer.write("\n")
    }

    override fun close() {
        reader.close()
        writer.close()
        socket.close()
    }
}
