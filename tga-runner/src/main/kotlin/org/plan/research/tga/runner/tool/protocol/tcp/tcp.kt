package org.plan.research.tga.runner.tool.protocol.tcp

import kotlinx.serialization.encodeToString
import org.plan.research.tga.core.tool.protocol.BenchmarkRequest
import org.plan.research.tga.core.tool.protocol.GenerationResult
import org.plan.research.tga.core.tool.protocol.Tga2ToolConnection
import org.plan.research.tga.core.tool.protocol.TgaServer
import org.plan.research.tga.core.tool.protocol.protocolJson
import java.net.ServerSocket
import java.net.Socket

class TcpTgaServer(
    val port: UInt
) : TgaServer {
    private val serverSocket = ServerSocket(port.toInt())

    override fun accept(): Tga2ToolConnection {
        val socket = serverSocket.accept()
        return TcpTga2ToolConnection(socket)
    }

    override fun close() {
        serverSocket.close()
    }
}

class TcpTga2ToolConnection(
    private val socket: Socket
) : Tga2ToolConnection {
    private val reader = socket.getInputStream().bufferedReader()
    private val writer = socket.getOutputStream().bufferedWriter()

    override fun send(request: BenchmarkRequest) {
        writer.write(protocolJson.encodeToString(request))
        writer.write("\n")
    }

    override fun receive(): GenerationResult {
        val json = reader.readLine()
        return protocolJson.decodeFromString(json)
    }

    override fun close() {
        reader.close()
        writer.close()
        socket.close()
    }
}

