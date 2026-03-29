package com.example.androidwebviewrequestreplaydemo

data class PendingCapturedRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>
)

data class CapturedRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val cookieHeader: String?
)
