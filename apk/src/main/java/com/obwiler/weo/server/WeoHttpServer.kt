package com.obwiler.weo.server

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 零外部依赖的轻量 HTTP 服务器。
 * 绑定 WEOApplication 生命周期，APK 启动即运行，关闭即停止。
 *
 * 架构：单线程 accept + 每连接一线程处理（设备间通信并发极低，够用）。
 */
class WeoHttpServer(
    private val port: Int = 8765,
    private val handler: suspend (WeoRequest) -> WeoResponse,
) {
    companion object {
        private const val TAG = "WEO/HttpServer"
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    /** 启动服务器。幂等——多次调用安全。 */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.d(TAG, "Server already running, ignoring start()")
            return
        }

        acceptThread = Thread({
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "HTTP server listening on port $port")

                while (running.get()) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (e: SocketException) {
                        if (!running.get()) break else continue
                    } ?: continue

                    Thread({
                        try {
                            handleClient(client)
                        } catch (_: Exception) {
                        } finally {
                            try { client.close() } catch (_: Exception) {}
                        }
                    }, "weo-http-client").start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server accept loop crashed", e)
            } finally {
                running.set(false)
            }
        }, "weo-http-accept").apply {
            isDaemon = true
            start()
        }
    }

    /** 停止服务器。幂等。 */
    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        Log.i(TAG, "Stopping HTTP server...")
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        acceptThread = null
    }

    val isRunning: Boolean get() = running.get()

    // ---- internal ----

    private fun handleClient(client: Socket) {
        client.soTimeout = 10_000 // 10s read timeout

        val reader = BufferedReader(InputStreamReader(client.getInputStream(), "UTF-8"))
        val writer = OutputStreamWriter(client.getOutputStream(), "UTF-8")

        try {
            // Parse request line
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ", limit = 3)
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val path = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                    if (key == "content-length") contentLength = value.toIntOrNull() ?: 0
                }
            }

            // Read body
            val body = if (contentLength > 0 && contentLength <= 1_048_576) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            // Dispatch to handler
            val request = WeoRequest(
                method = method,
                path = path,
                headers = headers,
                body = body,
                remoteAddr = client.inetAddress?.hostAddress ?: "unknown",
            )
            val response = try {
                runBlocking {
                    withTimeout(30_000) { handler(request) }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Handler timed out for ${method} ${path}")
                WeoResponse(504, "{\"error\":\"gateway timeout\"}")
            } catch (e: Exception) {
                Log.e(TAG, "Handler threw for ${method} ${path}", e)
                WeoResponse(500, "{\"error\":\"internal server error\"}")
            }

            // Write response
            writer.write("HTTP/1.1 ${response.status} ${statusText(response.status)}\r\n")
            response.headers.forEach { (k, v) -> writer.write("$k: $v\r\n") }
            if (!response.headers.containsKey("content-type")) {
                writer.write("Content-Type: application/json; charset=utf-8\r\n")
            }
            val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
            writer.write("Content-Length: ${bodyBytes.size}\r\n")
            writer.write("Connection: close\r\n")
            writer.write("Access-Control-Allow-Origin: *\r\n")
            writer.write("\r\n")
            writer.write(response.body)
            writer.flush()

            Log.d(TAG, "${method} ${path} → ${response.status}")
        } catch (_: SocketException) {
            // Client disconnected — normal
        } catch (e: Exception) {
            Log.w(TAG, "Client handling error", e)
        } finally {
            try { writer.close() } catch (_: Exception) {}
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        400 -> "Bad Request"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "Unknown"
    }
}

/** 内部请求模型 */
data class WeoRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
    val remoteAddr: String,
)

/** 内部响应模型 */
data class WeoResponse(
    val status: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun ok(body: String) = WeoResponse(200, body)
        fun okJson(json: String) = WeoResponse(200, json, mapOf("Content-Type" to "application/json; charset=utf-8"))
        fun notFound(msg: String = "{\"error\":\"not found\"}") = WeoResponse(404, msg)
        fun badRequest(msg: String = "{\"error\":\"bad request\"}") = WeoResponse(400, msg)
        fun internalError(msg: String = "{\"error\":\"internal error\"}") = WeoResponse(500, msg)
    }
}
