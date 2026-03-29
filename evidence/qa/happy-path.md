# Happy Path QA

## Goal

Verify the only promised flow builds and is ready for local interactive proof:

`load local page -> capture one GET request -> capture cookies -> replay with OkHttp`

## Command checks

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected result:

- `testDebugUnitTest` exits 0 with `NO-SOURCE` in this minimal repo
- the Android debug build exits 0
- the APK is produced at `app/build/outputs/apk/debug/app-debug.apk`

## Manual emulator/device QA

1. Launch the debug app
2. Confirm the WebView shows the controlled local page from `127.0.0.1`
3. Confirm one `/api/echo?...` GET request appears in the captured metadata panel
4. Confirm the cookie snapshot is visible in that panel
5. Tap **Replay captured GET**
6. Confirm the replay response shows HTTP 200 plus echoed cookie and `X-Demo-Header`

Pass condition: one controlled GET request can be captured and replayed without widening scope beyond the local page.

## Executed verification in this session

- `./gradlew testDebugUnitTest`: pass (`:app:testDebugUnitTest NO-SOURCE`)
- `./gradlew assembleDebug`: pass
- APK presence confirmed at `app/build/outputs/apk/debug/app-debug.apk`
- Manual emulator/device interaction was not executed in this session
