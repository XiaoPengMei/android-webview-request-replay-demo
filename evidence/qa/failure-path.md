# Failure Path QA

## Regression checks

### Failure case A: the local page loads but no request metadata appears

- Trigger: the WebView loads `/` but the controlled `/api/echo?...` request does not get captured
- Expectation: the app keeps showing the waiting state and replay stays disabled
- Likely checks: confirm JavaScript is enabled, the local server is running, and the request path still matches `/api/echo`

### Failure case B: replay runs without session cookies

- Trigger: the replay button runs but the echoed response body shows an empty or missing `receivedCookie`
- Expectation: replay should include the cookie snapshot from `CookieManager`
- Likely checks: confirm the root page still sets `demo_session=android-webview` and that replay uses the captured cookie header

### Failure case C: a non-GET request is selected

- Trigger: scope expands beyond the controlled GET request
- Expectation: this demo does not promise generic body capture or non-GET replay and should keep replay scoped to GET-only

Pass condition: the app fails honestly and keeps its scope boundary clear instead of pretending broader interception support.
