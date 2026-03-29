package com.example.androidwebviewrequestreplaydemo

import org.json.JSONObject
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalHttpServer : Closeable {

    private val running = AtomicBoolean(false)
    private val workerPool: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var port: Int = -1

    fun start(): String {
        if (running.get()) {
            return baseUrl()
        }

        val socket = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST))
        serverSocket = socket
        port = socket.localPort
        running.set(true)

        workerPool.execute {
            while (running.get()) {
                try {
                    val client = socket.accept()
                    workerPool.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) {
                        return@execute
                    }
                }
            }
        }

        return baseUrl()
    }

    fun baseUrl(): String = "http://$LOOPBACK_HOST:$port/"

    override fun close() {
        running.set(false)
        serverSocket?.close()
        workerPool.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            if (requestLine.isBlank()) return

            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase(Locale.US)
            val pathWithQuery = parts[1]
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    val key = line.substring(0, separator).trim()
                    val value = line.substring(separator + 1).trim()
                    headers[key] = value
                }
            }

            when {
                pathWithQuery == "/" -> writeResponse(
                    client.getOutputStream(),
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    body = buildIndexHtml(),
                    extraHeaders = mapOf("Set-Cookie" to "demo_session=android-webview; Path=/; HttpOnly")
                )

                pathWithQuery.startsWith("/api/echo") -> {
                    val body = JSONObject()
                        .put("method", method)
                        .put("path", pathWithQuery)
                        .put("receivedCookie", headers["Cookie"].orEmpty())
                        .put("receivedDemoHeader", headers["X-Demo-Header"].orEmpty())
                        .toString(2)
                    writeResponse(
                        client.getOutputStream(),
                        statusCode = 200,
                        contentType = "application/json; charset=utf-8",
                        body = body
                    )
                }

                pathWithQuery == "/favicon.ico" -> writeResponse(
                    client.getOutputStream(),
                    statusCode = 204,
                    contentType = "text/plain; charset=utf-8",
                    body = ""
                )

                else -> writeResponse(
                    client.getOutputStream(),
                    statusCode = 404,
                    contentType = "text/plain; charset=utf-8",
                    body = "not found"
                )
            }
        }
    }

    private fun writeResponse(
        outputStream: OutputStream,
        statusCode: Int,
        contentType: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            204 -> "No Content"
            404 -> "Not Found"
            else -> "OK"
        }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { (key, value) -> append("$key: $value\r\n") }
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        outputStream.write(response)
        outputStream.write(bodyBytes)
        outputStream.flush()
    }

    private fun buildIndexHtml(): String =
        """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Android WebView replay demo</title>
          <style>
            body { font-family: sans-serif; padding: 16px; }
            button { padding: 10px 14px; }
            pre { white-space: pre-wrap; background: #f3f4f6; padding: 12px; }
          </style>
        </head>
        <body>
          <h1>Controlled WebView page</h1>
          <p>This page only exists to trigger one same-origin GET request that the app can capture and replay.</p>
          <button id="run-request">Run controlled GET request</button>
          <p id="status">Waiting to trigger /api/echo...</p>
          <pre id="response">No request yet.</pre>
          <script>
            async function runRequest() {
              const status = document.getElementById('status');
              const responseNode = document.getElementById('response');
              status.textContent = 'Requesting /api/echo...';
              const response = await fetch('/api/echo?item=request-replay-demo', {
                method: 'GET',
                credentials: 'include',
                headers: {
                  'X-Demo-Header': 'from-webview'
                }
              });
              const body = await response.text();
              status.textContent = 'Controlled request finished.';
              responseNode.textContent = body;
            }

            document.getElementById('run-request').addEventListener('click', runRequest);
            window.addEventListener('load', () => {
              window.setTimeout(runRequest, 150);
            });
          </script>
        </body>
        </html>
        """.trimIndent()

    companion object {
        private const val LOOPBACK_HOST = "127.0.0.1"
    }
}
