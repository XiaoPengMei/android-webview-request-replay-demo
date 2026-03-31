package com.example.androidwebviewrequestreplaydemo

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.WebViewClient
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

    private enum class TestMode {
        CONTROLLED_REPLAY,
        GENERIC_URL_LOGIN
    }

    private lateinit var binding: ActivityMainBinding
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val httpClient = OkHttpClient()

    private var localHttpServer: LocalHttpServer? = null
    private var baseUrl: String = ""
    private var capturedRequest: CapturedRequest? = null
    private var currentMode: TestMode = TestMode.CONTROLLED_REPLAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        configureWebView()
        bindActions()
        binding.genericUrlEditText.setText(getString(R.string.default_generic_url))
        applyModeUi(TestMode.CONTROLLED_REPLAY)
        binding.modeRadioGroup.check(binding.controlledModeRadioButton.id)
        loadControlledPage()
    }

    private fun configureWebView() {
        binding.demoWebView.settings.javaScriptEnabled = true
        binding.demoWebView.settings.domStorageEnabled = true
    }

    private fun bindActions() {
        binding.loadButton.setOnClickListener { loadControlledPage() }
        binding.replayButton.setOnClickListener { replayCapturedRequest() }
        binding.loadGenericUrlButton.setOnClickListener { loadGenericUrl() }
        binding.modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = if (checkedId == binding.genericModeRadioButton.id) {
                TestMode.GENERIC_URL_LOGIN
            } else {
                TestMode.CONTROLLED_REPLAY
            }
            onModeSelected(selectedMode)
        }
    }

    private fun onModeSelected(mode: TestMode) {
        if (currentMode == mode) {
            return
        }

        applyModeUi(mode)
        if (mode == TestMode.CONTROLLED_REPLAY) {
            loadControlledPage()
        } else {
            binding.demoWebView.stopLoading()
            capturedRequest = null
            binding.replayButton.isEnabled = false
            binding.replayResponseTextView.text = getString(R.string.replay_disabled_generic_mode)
            binding.capturedRequestTextView.text = getString(R.string.captured_not_available_generic_mode)
            binding.statusTextView.text = getString(R.string.status_generic_mode_idle)
            binding.demoWebView.webViewClient = genericModeWebViewClient()
        }
    }

    private fun applyModeUi(mode: TestMode) {
        currentMode = mode
        val controlledVisible = mode == TestMode.CONTROLLED_REPLAY
        binding.controlledActionsContainer.visibility = if (controlledVisible) View.VISIBLE else View.GONE
        binding.genericUrlContainer.visibility = if (controlledVisible) View.GONE else View.VISIBLE
        binding.capturedRequestLabelTextView.visibility = if (controlledVisible) View.VISIBLE else View.GONE
        binding.capturedRequestContainer.visibility = if (controlledVisible) View.VISIBLE else View.GONE
        binding.replayResponseLabelTextView.visibility = if (controlledVisible) View.VISIBLE else View.GONE
        binding.replayResponseContainer.visibility = if (controlledVisible) View.VISIBLE else View.GONE
    }

    private fun loadControlledPage() {
        if (currentMode != TestMode.CONTROLLED_REPLAY) {
            return
        }

        val server = localHttpServer
        if (server == null) {
            localHttpServer = LocalHttpServer()
        }

        val activeServer = localHttpServer ?: return
        baseUrl = activeServer.start()
        val controlledBaseUrl = baseUrl
        capturedRequest = null
        binding.statusTextView.text = getString(R.string.status_loading_controlled_page, baseUrl)
        binding.capturedRequestTextView.text = getString(R.string.no_request_captured)
        binding.replayButton.isEnabled = false
        binding.replayResponseTextView.text = getString(R.string.no_replay_yet)
        binding.demoWebView.webViewClient = CaptureWebViewClient(
            baseUrl = controlledBaseUrl,
            onRequestCaptured = { pending ->
                runOnUiThread {
                    if (currentMode != TestMode.CONTROLLED_REPLAY || !pending.url.startsWith(controlledBaseUrl)) {
                        return@runOnUiThread
                    }
                    val cookieHeader = CookieManager.getInstance().getCookie(pending.url)
                        ?: CookieManager.getInstance().getCookie(controlledBaseUrl)
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
                        getString(R.string.status_controlled_replay_ready)
                    } else {
                        getString(R.string.status_controlled_get_only)
                    }
                }
            },
            onPageStarted = { pageUrl ->
                runOnUiThread {
                    if (currentMode != TestMode.CONTROLLED_REPLAY || !pageUrl.startsWith(controlledBaseUrl)) {
                        return@runOnUiThread
                    }
                    binding.statusTextView.text = getString(R.string.status_webview_loading, pageUrl)
                }
            },
            onPageFinished = { pageUrl ->
                runOnUiThread {
                    if (currentMode != TestMode.CONTROLLED_REPLAY || !pageUrl.startsWith(controlledBaseUrl)) {
                        return@runOnUiThread
                    }
                    val cookies = CookieManager.getInstance().getCookie(pageUrl)
                    if (capturedRequest == null && !cookies.isNullOrBlank()) {
                        binding.capturedRequestTextView.text = buildString {
                            appendLine(getString(R.string.controlled_session_cookie_label))
                            appendLine(cookies)
                            appendLine()
                            append(getString(R.string.controlled_waiting_for_metadata))
                        }
                    }
                }
            }
        )
        binding.demoWebView.loadUrl(controlledBaseUrl)
    }

    private fun loadGenericUrl() {
        if (currentMode != TestMode.GENERIC_URL_LOGIN) {
            return
        }

        val inputUrl = binding.genericUrlEditText.text?.toString()?.trim().orEmpty()
        if (inputUrl.isEmpty()) {
            binding.statusTextView.text = getString(R.string.error_generic_url_empty)
            return
        }
        if (!isSupportedHttpUrl(inputUrl)) {
            binding.statusTextView.text = getString(R.string.error_generic_url_invalid_scheme)
            return
        }

        capturedRequest = null
        binding.replayButton.isEnabled = false
        binding.replayResponseTextView.text = getString(R.string.replay_disabled_generic_mode)
        binding.capturedRequestTextView.text = getString(R.string.captured_not_available_generic_mode)
        binding.statusTextView.text = getString(R.string.status_loading_generic_url, inputUrl)
        binding.demoWebView.webViewClient = genericModeWebViewClient()
        binding.demoWebView.loadUrl(inputUrl)
    }

    private fun isSupportedHttpUrl(rawUrl: String): Boolean {
        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.US)
        val host = uri.host.orEmpty()
        return (scheme == "http" || scheme == "https") && host.isNotBlank()
    }

    private fun genericModeWebViewClient(): WebViewClient = object : WebViewClient() {
        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (currentMode == TestMode.GENERIC_URL_LOGIN && !url.isNullOrBlank()) {
                binding.statusTextView.text = getString(R.string.status_loading_generic_url, url)
            }
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            if (currentMode == TestMode.GENERIC_URL_LOGIN && !url.isNullOrBlank()) {
                binding.statusTextView.text = getString(R.string.status_generic_url_loaded, url)
            }
            super.onPageFinished(view, url)
        }

        override fun onReceivedError(
            view: android.webkit.WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (
                currentMode == TestMode.GENERIC_URL_LOGIN &&
                request?.isForMainFrame == true
            ) {
                val failingUrl = request.url?.toString().orEmpty()
                val errorText = error?.description?.toString().orEmpty()
                binding.statusTextView.text = getString(
                    R.string.status_generic_url_failed,
                    failingUrl,
                    errorText
                )
            }
            super.onReceivedError(view, request, error)
        }
    }

    private fun replayCapturedRequest() {
        if (currentMode != TestMode.CONTROLLED_REPLAY) {
            binding.statusTextView.text = getString(R.string.replay_disabled_generic_mode)
            return
        }

        val requestToReplay = capturedRequest
        if (requestToReplay == null) {
            binding.statusTextView.text = getString(R.string.status_no_captured_request)
            return
        }
        if (!requestToReplay.method.equals("GET", ignoreCase = true)) {
            binding.statusTextView.text = getString(R.string.status_controlled_get_only)
            return
        }

        binding.statusTextView.text = getString(
            R.string.status_replaying_request,
            requestToReplay.method.uppercase(Locale.US),
            requestToReplay.url
        )
        binding.replayResponseTextView.text = getString(R.string.status_running_replay)

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
                            appendLine(getString(R.string.replay_response_status, response.code, response.message))
                            appendLine(getString(R.string.replay_response_cookie, requestToReplay.cookieHeader.orEmpty()))
                            appendLine()
                            appendLine(getString(R.string.replay_response_body_label))
                            append(responseBody)
                        }
                        binding.statusTextView.text = getString(R.string.status_replay_completed, response.code)
                    }
                }
            } catch (error: IOException) {
                runOnUiThread {
                    binding.replayResponseTextView.text = getString(
                        R.string.status_replay_failed_detail,
                        error.message.orEmpty()
                    )
                    binding.statusTextView.text = getString(R.string.status_replay_failed)
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
