# Demand Evidence

## Public demand signal

- Primary source: `https://github.com/react-native-webview/react-native-webview/issues/3688`
- Repeated adjacent signals: `flutter_inappwebview#578`, `flutter_inappwebview#2392`, `flutter_inappwebview#2580`
- Observation date used for freshness: `2026-03-29`
- Problem: developers still ask for a small, trustworthy way to inspect one WebView request and replay it without jumping straight to a full proxy or arbitrary-site interception story.

## Why this repo exists

This repo keeps the proof small and local:

`controlled local page -> one captured GET request -> cookie snapshot -> one OkHttp replay`

By serving the page from an in-app loopback server, the demo can prove the capture/replay path without pretending it can inspect any remote site.

## Scope contract

- Keep: native Android, `WebViewClient.shouldInterceptRequest`, `CookieManager`, one replayed GET request.
- Exclude: arbitrary-site sniffing, MITM claims, generic POST body capture, remote proxy infrastructure.
