# SteamDeals 安卓版（原生 Android App）

SteamDeals 原生 Android 客户端（Kotlin + Jetpack Compose），与站点 Web 完全拆分、共用同一后端 API。
规格与排期唯一来源：站点仓库 `gameprice` 的 [`docs/APP-NATIVE.md`](../gameprice/docs/APP-NATIVE.md)（§3.4/§4/§10）
与 [`docs/APP-API.md`](../gameprice/docs/APP-API.md)（接口一律按它）；TODO 编号 **CG（CA-2~CA-4）**。

## 关键参数

| 项 | 值 |
|---|---|
| 包名 | `cn.yyliucha.steamdeals`（复用旧 TWA 包名，**同 keystore 覆盖升级，用户无需卸载**） |
| 站点根 | `https://sd.yyliucha.cn`（全部 API 走 `/api/app/*` 命名空间） |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| 版本规则 | `versionCode = major*10000 + minor*100 + patch`，必须单调递增（旧 TWA 停在 21800） |
| 当前版本 | 2.19.0 / 21900 |
| 技术栈 | Kotlin 2.0 + Compose（Material 3）+ Navigation Compose + OkHttp + kotlinx.serialization + Coil + DataStore；不引 Hilt/Retrofit（手写 ServiceLocator） |
| 签名 | keystore **不入库**（本机 `C:\Users\Administrator\.steamdeals\android.keystore`，密码在同目录 `keystore-pass.txt`；指纹 SHA-256 `48:A3:0D:F9:…:7A:05`） |

## 功能（CG 批）

- **CA-2 骨架**：Compose 单 Activity + 底部 4 Tab（首页/搜索/愿望单/我的）+ 导航；OkHttp 网络层
  （Bearer Token + `X-Device-Id` + 统一错误映射：401 `login_required` / 403 `must_change_password` / 429）；
  DataStore 持久化（Token/过期时间/用户/设备 UUID/主题/搜索历史/游客公告已读）；登录页（用户名密码 +
  图形验证码 + 记住我 30 天）+ 注册页（邮箱验证码流程）。
- **CA-3 内容**：首页（搜索入口、筛选 chips 全部/折扣/史低/免费/未发售、排序、加载更多）、搜索页
  （自动聚焦、本地历史）、详情页（封面/指标/描述/各区服报价/价格历史 Canvas 原生绘制/DLC 列表/
  Steam 与购买链接走 Custom Tabs）。
- **CA-4 账号**：愿望单（与 web 账号共享、已降价/史低筛选、移除）、我的（账号/通知/主题/检查更新/
  修改密码/关于/退出登录）、内置更新（启动 + 回前台 24h 节流 + 手动；Wi-Fi 默认；SHA-256 校验；
  系统安装确认；`minVersionCode` 强制更新）、启动封禁检查（`/api/app/status` 命中 → 「你已经被封禁」+
  「已知晓」→ 退出 App）。
- **游客模式**：浏览不强制登录；点「感兴趣/不感兴趣/愿望单」弹「登录后才能使用该功能」；
  登录成功后回到原页面并**自动完成刚才的操作一次**（`pendingAction`，取消/失败清空、不循环）；
  「愿望单」「我的」未登录显示登录引导页。

## 构建

```bash
# 环境：JDK 17 + ANDROID_HOME=C:\Android\sdk（platforms;android-36 / build-tools;35.0.0）
.\gradlew.bat assembleDebug      # 调试包（本机联调可用 -PapiBaseUrl=http://10.0.2.2:PORT 指本地后端）
.\gradlew.bat assembleRelease    # 发布包（读取根目录 keystore.properties，缺失则出未签名包）
```

签名参数（三选一，按优先级）：`-PstoreFile/-PstorePassword/-PkeyAlias/-PkeyPassword` →
环境变量同名大写（未实现，见 keystore.properties）→ 根目录 `keystore.properties`（gitignore）：
`storeFile / storePassword / keyAlias=steamdeals / keyPassword`。签名后核对指纹：

```bash
keytool -printcert -jarfile app\build\outputs\apk\release\app-release.apk | findstr SHA256
# 预期 48:A3:0D:F9:D0:BF:7E:CC:53:EC:E0:AF:2B:69:C4:D4:50:53:04:AD:3B:A5:A2:D3:8F:92:C7:97:D0:F1:7A:05
```

## 代码地图

| 关注点 | 落点 |
|---|---|
| 网络层 / 错误映射 / 下载校验 | `data/api/ApiClient.kt`、`ApiException.kt`、`SteamDealsApi.kt`（全部接口）、`Dtos.kt` |
| 本地持久化 | `data/store/SessionStore.kt`（DataStore） |
| 会话 / pendingAction | `data/SessionManager.kt`、`data/PendingAction.kt` |
| 全局状态 | `ui/AppViewModel.kt`（会话/主题/封禁检查/更新状态/登录门槛事件） |
| 导航 + 4 Tab | `ui/AppNav.kt` |
| 六屏 | `ui/home`、`ui/search`、`ui/detail`、`ui/wishlist`、`ui/me`、`ui/login`（+ `ui/register`/`ui/notifications`/`ui/password`/`ui/about`） |
| 内置更新 | `ui/update/UpdateManager.kt`、`UpdateDialogHost.kt`、`InstallResultReceiver.kt` |
| 主题 | `ui/theme`（Material 3 + 品牌紫 `#8b82f5`、页面底色 `#0f1524`） |

## 已知限制（v1，与站点仓库 CG 条目同步）

1. 首页「折扣/史低/免费/未发售」筛选与「热度/发行时间/价格」排序目前按**已加载数据本地处理**
   （列表行自带 `discount/prev_hist_low/release_date/heat` 字段）；服务端 `/api/games` 补对应
   `filter` 参数后，只需替换 `HomeScreenState.applyDisplay`，App 无需改版。
2. 「感兴趣/不感兴趣」按契约走 `POST /api/games/:appid/interest`（APP-API.md §4，普通用户可操作）；
   该路由当前在站点侧仍是管理员专用（CA-1 收尾前）→ App 收到 403 时自动回退现有用户级
   `/api/games/:appid/flags`（请求体相同）；契约端点放开后无需改 App。
3. 内置更新下载用 OkHttp 流式 + 应用内进度条（BY 契约的 DownloadManager 是 TWA 实现细节；
   SHA-256 校验 / Wi-Fi 默认 / 系统安装确认 / 白名单主机均与契约一致）。
4. 注册完成后回登录页预填用户名：App 的 Token 登录仍需再输一次图形验证码（服务端验证码必填）。
5. 「已拥有」标记第一期未做（APP-API.md 未提供 App 端 owned 接口；web 有）。

## 本批不做

管理界面（后置）、iOS、推送通知、离线全量缓存、Steam 账号绑定/同步、TWA 相关代码。
