package com.example.androidwebviewrequestreplaydemo

import android.os.Bundle
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatActivity
import com.example.androidwebviewrequestreplaydemo.databinding.ActivityMainBinding
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val httpClient = OkHttpClient()

    private var localHttpServer: LocalHttpServer? = null
    private var baseUrl: String = ""
    private var capturedRequest: CapturedRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        configureWebView()
        bindActions()
        loadControlledPage()
    }

    private fun configureWebView() {
        binding.demoWebView.settings.javaScriptEnabled = true
    }

    private fun bindActions() {
        binding.loadButton.setOnClickListener { loadControlledPage() }
        binding.replayButton.setOnClickListener { replayCapturedRequest() }
    }

    private fun loadControlledPage() {
        val server = localHttpServer
        if (server == null) {
            localHttpServer = LocalHttpServer()
        }

        val activeServer = localHttpServer ?: return
        baseUrl = activeServer.start()
        capturedRequest = null
        binding.statusTextView.text = "Loading controlled page from $baseUrl"
        binding.capturedRequestTextView.text = "Waiting for /api/echo GET metadata from the controlled page..."
        binding.replayButton.isEnabled = false
        binding.replayResponseTextView.text = getString(R.string.no_replay_yet)
        binding.demoWebView.webViewClient = CaptureWebViewClient(
            baseUrl = baseUrl,
            onRequestCaptured = { pending ->
                runOnUiThread {
                    val cookieHeader = CookieManager.getInstance().getCookie(pending.url)
                        ?: CookieManager.getInstance().getCookie(baseUrl)
                    capturedRequest = CapturedRequest(
                        url = pending.url,
                        method = pending.method,
                        headers = pending.headers,
                        cookieHeader = cookieHeader
                    )
                    binding.capturedRequestTextView.text = formatCapturedRequest(capturedRequest!!)
                    val replayable = pending.method.equals("GET", ignoreCase = true)
                    binding.replayButton.isEnabled = replayable
                    binding.statusTextView.text = if (replayable) {
                        "Captured one controlled GET request. Replay is ready."
                    } else {
                        "Captured a request, but replay stays GET-only in this demo."
                    }
                }
            },
            onPageStarted = { pageUrl ->
                runOnUiThread {
                    binding.statusTextView.text = "WebView loading $pageUrl"
                }
            },
            onPageFinished = { pageUrl ->
                runOnUiThread {
                    val cookies = CookieManager.getInstance().getCookie(pageUrl)
                    if (capturedRequest == null && !cookies.isNullOrBlank()) {
                        binding.capturedRequestTextView.text = buildString {
                            appendLine("Cookies captured for the controlled session:")
                            appendLine(cookies)
                            appendLine()
                            append("Waiting for /api/echo GET metadata...")
                        }
                    }
                }
            }
        )
        binding.demoWebView.loadUrl(baseUrl)
    }

    private fun replayCapturedRequest() {
        val requestToReplay = capturedRequest
        if (requestToReplay == null) {
            binding.statusTextView.text = "No captured request is available yet."
            return
        }
        if (!requestToReplay.method.equals("GET", ignoreCase = true)) {
            binding.statusTextView.text = "This demo only replays captured GET requests."
            return
        }

        binding.statusTextView.text = "Replaying ${requestToReplay.method.uppercase(Locale.US)} ${requestToReplay.url}"
        binding.replayResponseTextView.text = "Running OkHttp replay..."

        backgroundExecutor.execute {
            try {
                val request = Request.Builder()
                    .url(requestToReplay.url)
                    .get()
                    .headers(buildReplayHeaders(requestToReplay))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    runOnUiThread {
                        binding.replayResponseTextView.text = buildString {
                            appendLine("Status: ${response.code} ${response.message}")
                            appendLine("Used Cookie header: ${requestToReplay.cookieHeader.orEmpty()}")
                            appendLine()
                            appendLine("Body:")
                            append(responseBody)
                        }
                        binding.statusTextView.text = "Replay completed with HTTP ${response.code}."
                    }
                }
            } catch (error: IOException) {
                runOnUiThread {
                    binding.replayResponseTextView.text = "Replay failed: ${error.message.orEmpty()}"
                    binding.statusTextView.text = "Replay failed."
                }
            }
        }
    }

    private fun buildReplayHeaders(requestToReplay: CapturedRequest): Headers {
        val allowedHeaders = setOf("Accept", "Accept-Language", "User-Agent", "X-Demo-Header")
        val builder = Headers.Builder()
        requestToReplay.headers.forEach { (key, value) ->
            if (allowedHeaders.contains(key)) {
                builder.add(key, value)
            }
        }
        requestToReplay.cookieHeader
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.set("Cookie", it) }
        return builder.build()
    }

    private fun formatCapturedRequest(request: CapturedRequest): String = buildString {
        appendLine("URL: ${request.url}")
        appendLine("Method: ${request.method}")
        appendLine("Cookie snapshot: ${request.cookieHeader.orEmpty()}")
        appendLine()
        appendLine("Headers:")
        request.headers.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (key, value) ->
            appendLine("$key: $value")
        }
    }

    override fun onDestroy() {
        binding.demoWebView.destroy()
        localHttpServer?.close()
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }
}
