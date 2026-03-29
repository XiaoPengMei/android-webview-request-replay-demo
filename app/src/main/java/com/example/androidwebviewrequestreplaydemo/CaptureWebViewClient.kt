package com.example.androidwebviewrequestreplaydemo

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class CaptureWebViewClient(
    private val baseUrl: String,
    private val onRequestCaptured: (PendingCapturedRequest) -> Unit,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url != null) {
            onPageStarted(url)
        }
        super.onPageStarted(view, url, favicon)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request != null) {
            val requestUrl = request.url.toString()
            val requestPath = request.url.path.orEmpty()
            if (requestUrl.startsWith(baseUrl) && requestPath == "/api/echo") {
                onRequestCaptured(
                    PendingCapturedRequest(
                        url = requestUrl,
                        method = request.method,
                        headers = LinkedHashMap(request.requestHeaders)
                    )
                )
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null) {
            onPageFinished(url)
        }
        super.onPageFinished(view, url)
    }
}
