# Android WebView request-replay demo

这是一个**收敛范围的原生 Android 演示仓库**，只证明一个非常具体的调试路径：

> 在受控的 WebView 页面里捕获 1 条 GET 请求和当前会话 Cookie，再在应用内用 OkHttp 按同一会话头信息重放这条请求。

需求信号来自重复出现的 WebView 请求检查问题，例如 `react-native-webview/react-native-webview#3688`，以及相邻生态里的 `flutter_inappwebview#578`、`#2392`、`#2580`。

## 这个 demo 证明什么

- 应用会在 `http://127.0.0.1:<port>/` 启动一个极小的内置 HTTP 服务
- `WebView` 加载这个受控本地页面
- 页面会主动发起一个同源 `GET /api/echo?...` 请求，并带上演示 Header
- `WebViewClient.shouldInterceptRequest` 会捕获这条请求的 URL、Method 和请求头
- `CookieManager` 会读取当前 WebView 会话的 Cookie 状态
- 点击按钮后，应用会用 OkHttp 复用捕获到的 Cookie / 选定 Header 来重放这条 GET 请求
- 应用界面会展示捕获到的请求元数据和重放响应内容

## 范围约束

这个仓库**故意只做很小的本地证明**。

- 保留：1 个受控本地页面、1 条被捕获的 GET 请求、1 条应用内重放路径
- 保留：从本地 WebView 会话读取 Cookie
- 不做：任意网站抓包、MITM、中间代理、通用流量拦截、通用 POST body 捕获/重放
- 不做：React Native、Expo、Flutter、远程后端服务、外部代理

如果目标变成“非 GET 请求 body 捕获”或“任意网页接口抓取”，那已经是一个更大且不同的项目，不属于这个 demo 的承诺范围。

## 项目结构

```text
app/
  src/main/java/com/example/androidwebviewrequestreplaydemo/
    MainActivity.kt
    LocalHttpServer.kt
    CaptureWebViewClient.kt
    CapturedRequest.kt
  src/main/res/
evidence/
  demand.md
  selection-scorecard.md
  qa/happy-path.md
  qa/failure-path.md
  release.md
```

## 本地构建

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

调试 APK 输出位置：`app/build/outputs/apk/debug/`。

这个最小仓库没有额外加入单元测试，所以 `testDebugUnitTest` 预期会返回 `NO-SOURCE`；实际执行结果记录在 `evidence/release.md` 里。

## 手动演示路径

1. 安装并打开 debug app
2. 确认 WebView 加载了本地 `127.0.0.1` 页面
3. 等待或手动触发受控 `GET /api/echo?...` 请求
4. 确认界面展示了捕获到的 URL、Method、Headers 和 Cookie 快照
5. 点击 **Replay captured GET**
6. 确认重放结果里能看到 HTTP 200，以及回显的 Cookie 和演示 Header

这条 happy path 已经写入 `evidence/qa/happy-path.md`。最新一次已执行的验证结果以 `evidence/release.md` 为准。

## 证据文件

- 需求证据：`evidence/demand.md`
- 选题评分：`evidence/selection-scorecard.md`
- 正向 QA：`evidence/qa/happy-path.md`
- 失败路径 QA：`evidence/qa/failure-path.md`
- 发布证据：`evidence/release.md`

## English summary

This repo is a deliberately narrow native Android proof: load a controlled local page in WebView, capture one GET request plus session cookies, and replay that request in-app with OkHttp. It does **not** claim arbitrary-site sniffing, MITM behavior, or generic POST-body replay.
