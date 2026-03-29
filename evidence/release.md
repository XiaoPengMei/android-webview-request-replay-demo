# Release Evidence

## Release target

Local-only release readiness for `android-webview-request-replay-demo`.

## Completed deliverables

- Created a fresh native Android child repo with its own Git history
- Added a single-activity Kotlin app with an in-app loopback HTTP server
- Loaded a controlled local page into `WebView`
- Captured one controlled request's URL, method, and headers via `shouldInterceptRequest`
- Captured session cookies with `CookieManager`
- Replayed the captured GET request with OkHttp using the captured cookie/header state
- Added README plus demand, scorecard, QA, and release evidence files

## Verification evidence

Executed locally:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Observed:

- `testDebugUnitTest`: pass with `NO-SOURCE`
- `assembleDebug`: pass
- debug APK is emitted to `app/build/outputs/apk/debug/app-debug.apk`
- manual emulator/device UI proof remains documented in `evidence/qa/happy-path.md` and was not executed in this session

## Scope check

- One controlled local page served from `127.0.0.1`
- One captured GET replay path
- Cookie capture for the same local WebView session
- No arbitrary-site sniffing
- No MITM claim
- No generic POST body capture/replay

## Scope confirmation

This release is intentionally a minimal local proof, not a universal WebView traffic interception tool.
