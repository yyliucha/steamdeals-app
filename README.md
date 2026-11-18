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
| 当前版本 | 2.20.0 / 22000 |
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
- **CM 批补齐（2.20.0，站点仓库 TODO CM，规格 docs/APP-PARITY.md）**：
  - 开发商/发行商名称解析修复（服务端历史数据有 JSON 对象文本形态，现在统一取 `name`，不再显示原始 JSON）；
  - DLC 区块：详情底部「DLC（共 N 个）」、10 条/页翻页、价格/折扣/缺货、点行跳 Steam 商店、标注「Steam 不提供 DLC 拥有数据」；无 DLC/非 Steam 不显示；
  - 价格历史：近 30 天/近 90 天/全部区间切换（默认 90）+ 指标条（区间最低/区间最高/当前较史低/记录天数）+
    史低虚线参考线与全部史低日绿点，口径与 Web 一致（PRICE-HISTORY-REPORT §11）；
  - 详情标题旁「复制游戏名」按钮（中英文两行，缺一回退单行，Toast 提示）。
- **CA-4 账号**：愿望单（与 web 账号共享、已降价/史低筛选、移除）、我的（账号/公告/主题/检查更新/
  修改密码/关于/退出登录）、内置更新（启动 + 回前台 24h 节流 + 手动；Wi-Fi 默认；SHA-256 校验；
  系统安装确认；`minVersionCode` 强制更新）、启动封禁检查（`/api/app/status` 命中 → 「你已经被封禁」+
  「已知晓」→ 退出 App）。
- **CX 批（站点 TODO CX，个人资料编辑，docs/USER-PROFILE-EDIT.md）**：
  - 入口：「我的 → 编辑资料」（账号卡点击直达）；头像显示 `/api/avatar/:userId`（无头像回退首字）；
  - 头像：相册选图 → 客户端裁剪 256×256 → dataUrl 上传（`POST /api/app/me/avatar`）；来源 chips
    （各 Steam 账号 / 自定义）；移除；
  - 资料：昵称（2–20 字）/ 简介（≤200 字）/ 主题（跟随系统/深/浅）/ 到价提醒邮件开关；
    用户名/邮箱/角色只读；管理员昵称/简介可空；
  - Steam 账号卡：每账号一张卡（头像/昵称/SteamID/已拥有/愿望单；Android 不显示最近同步）、
    设为默认（头像默认取该账号）/ 解绑（二次确认，保留历史）/ ＋绑定新的（系统浏览器打开站点绑定流程，
    返回 App 自动刷新）；
  - 我的统计：已拥有合计 / 愿望单合计 / 本站愿望单 / 到价提醒 / 史低命中（5 项）；
  - 查看已拥有/愿望单：底部弹层（tab + 搜索 + 分页，与 Web 对话框同结构）。
- **DC 批（站点 TODO DC，CM-6 公告角标与公告列表，纯 App 端）**：
  - 入口：首页右上角铃铛 + 「我的 → 公告」，共用同一公告列表弹层；
  - 铃铛角标 = 未读公告数（所有 active 公告中不在 readIds 的；0 隐藏；点开列表逐条已读、角标递减）；
  - 列表弹层：标题 / 级别色点（critical 红、warning 橙、info 蓝）/ 时间（站点时区 Asia/Shanghai）/ 已读态，
    点开详情（进入即上报已读）、下拉刷新；
  - 游客可看公告，已读存本地 DataStore（重启保留）；登录后本地已读自动合并到服务端
    （`POST /api/app/announcements/read`，服务端幂等）。
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
| 网络层 / 错误映射 / 下载校验 | `data/api/ApiClient.kt`、`ApiException.kt`、`SteamDealsApi.kt`（全部接口）、`Dtos.kt`（含 `NameListSerializer`） |
| 本地持久化 | `data/store/SessionStore.kt`（DataStore） |
| 会话 / pendingAction | `data/SessionManager.kt`、`data/PendingAction.kt` |
| 全局状态 | `ui/AppViewModel.kt`（会话/主题/封禁检查/更新状态/登录门槛事件） |
| 导航 + 4 Tab | `ui/AppNav.kt` |
| 六屏 | `ui/home`、`ui/search`、`ui/detail`、`ui/wishlist`、`ui/me`、`ui/login`（+ `ui/register`/`ui/notifications`/`ui/password`/`ui/about`/`ui/profile`） |
| 价格历史口径（CM-3） | `ui/components/PriceHistoryModel.kt`（区间/指标纯函数）、`PriceHistoryChart.kt`（Canvas 趋势线） |
| 复制名口径（CM-4） | `ui/components/GameNameText.kt`（`nameCopyText`，与 Web utils/gameNames.js 同口径） |
| 公告（DC） | `ui/notifications/AnnouncementsModel.kt`（未读/合并/级别/时间纯函数）、`AnnouncementsState.kt`（全局状态）、`AnnouncementsOverlay.kt`（列表弹层 + 详情）；入口：`HomeScreen.kt` 铃铛、`MeScreen.kt`「公告」；登录合并：`AppViewModel.onTokenLogin` |
| 单元测试 | `app/src/test/`（`NameListSerializerTest` / `PriceHistoryModelTest` / `GameNameTextTest` / `AnnouncementsModelTest` / `ProfileValidationTest` / `ProfileRequestsTest`，`gradlew testDebugUnitTest`） |
| 内置更新 | `ui/update/UpdateManager.kt`、`UpdateDialogHost.kt`、`InstallResultReceiver.kt` |
| 主题 | `ui/theme`（Material 3 + 品牌紫 `#8b82f5`、页面底色 `#0f1524`） |

## 已知限制（与站点仓库 CM 条目同步）

1. ~~首页「折扣/史低/免费/未发售」筛选与「热度/发行时间/价格」排序目前按**已加载数据本地处理**~~
   **DA 批（站点 TODO DA，CM-5 二期）已服务端化**：chip/排序变更带服务端参数重新请求第一页
   （`discountOnly/histLowOnly/freeOnly/unreleased` 互斥 + `sort=heat|release|price` + `owned=only` 已拥有 chip，
   仅登录且已绑定 Steam 显示；空态「没有符合条件的游戏」）；App 无需再本地过滤。
2. 「感兴趣/不感兴趣」按契约走 `POST /api/games/:appid/interest`（APP-API.md §4，普通用户可操作）；
   该路由当前在站点侧仍是管理员专用（CA-1 收尾前）→ App 收到 403 时自动回退现有用户级
   `/api/games/:appid/flags`（请求体相同）；契约端点放开后无需改 App。
3. 内置更新下载用 OkHttp 流式 + 应用内进度条（BY 契约的 DownloadManager 是 TWA 实现细节；
   SHA-256 校验 / Wi-Fi 默认 / 系统安装确认 / 白名单主机均与契约一致）。
4. 注册完成后回登录页预填用户名：App 的 Token 登录仍需再输一次图形验证码（服务端验证码必填）。
5. 「已拥有」chip（DA 批）复用站点 `owned=only`（多账号 Steam 库存并集）；Steam 账号**绑定**仍在网页版
   控制台完成（App 无绑定入口），未绑定用户看不到该 chip。
6. DLC 列表依赖站点 `game_dlc` 数据同步（随 steam-status/backfill 顺带写入）；数据未同步的游戏
   详情不显示 DLC 区块（无 DLC/非 Steam 同规则，按 CM 规格）。
7. 复制游戏名复制**中英文两行**（英文缺失/与中文相同回退单行），与 Web 详情弹窗口径一致。

## 本批不做

管理界面（后置）、iOS、推送通知、离线全量缓存、Steam 账号绑定/同步、TWA 相关代码。
