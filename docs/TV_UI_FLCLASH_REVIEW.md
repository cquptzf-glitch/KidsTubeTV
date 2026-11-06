# KidsTubeTV TV UI 简化方案与 FlClash 原生实现评估

审核日期：2026-11-06。目标机型：Android TV 9 / API 28（实际固件以 adb 为准）。此文档是**设计与依赖选型**，不是声称工程已经迁移。

## 一、电视 UI：儿童端极简

原则：**一次进入、一次选择、一次播放**。避免让孩子在 TV 上输入内容或操作节点。

- 导航：默认 72dp 图标栏（首页、动物、英文儿歌、收藏），焦点进入时自动展开至 ~220dp；失焦自动收起。最底部一个「家长设置」入口。
- 沉浸首页：一张高对比度的主推荐卡片，单一「继续播放」按钮；下方三列 16:9 大卡片（自适应 1080p 实际安全区域），每张仅封面、标题和频道名称，无收藏/分享等独立小按钮。
- 焦点：方向键空间邻接规则；左键从首列进入窄侧栏，右键返回内容；焦点放大约 1.04x 并有明显描边；返回键先关闭浮层/返回上级，再返回儿童首页。
- 播放：遥控器 OK/播放键直接切换播放暂停，左右键 ±10s；播放器控制 UI 延时隐藏，无底部常驻 5 个按钮。
- 家长中心：只显示「儿童内容」「网络代理」「YouTube/Google」3 个入口。PIN 仅在进入家长中心时验证。
- 网络设置：只显示状态、当前节点、订阅来源；默认自动优选；真正的订阅 URL 和复杂规则输入建议通过手机上的本地配网页实现（需要独立安全设计及配对代码，不可把 Demo 当成实际可用功能）。
- 字幕、网速、日志、流量统计、订阅规则等复杂项默认隐藏在高级页面。
- 设计参考离线 `demo.html` 最新沉浸版：**HTML 不等于 Kotlin Android TV 已实现此布局**。

### 验收标准

1. 儿童首页首次出现焦点，无须点击「连接代理」或手动选择节点。
2. 从启动到播放批准视频在正常配置的情况下不超过 3 次遥控器 OK。
3. 大卡片焦点可用方向键完整遍历；没有焦点丢失/卡住/滚动后看不见的目标。
4. 儿童不能访问 Clash、账户配置及白名单编辑；PIN 不使用隐藏默认值。
5. TV 720p/1080p/4K（UI 通常以 1080p dp 适配）可读性、焦点与安全区域通过验证。

## 二、FlClash 源码核对

官方维护仓库：<https://github.com/chen08209/FlClash>，GPL-3.0。Android 最低 SDK=24（官方 `android/gradle/libs.versions.toml`），覆盖 Android 9 API 28。

重要结构：

| 源码 | 作用 |
| --- | --- |
| `android/service/src/main/java/com/follow/clash/service/VpnService.kt` | Android TUN, IPv4/IPv6, DNS, 按应用代理与生命周期 |
| `android/app/src/main/kotlin/com/follow/clash/ServiceController.kt` | 核心与 service 绑定/解绑、取消与恢复 |
| `android/core/src/main/java/com/follow/clash/core/Core.kt` | Android Kotlin ↔ Go `Mobile` 的薄桥 |
| `core/mobile/mobile.go` | Go gomobile 导出接口 |
| `core/lib.go` | 内核运行时、TUN、protect() 回调、事件 |
| `android/core/build.gradle.kts` | Go + gomobile + Android NDK 产出 Java/JNI 与 libclash.so |
| `android/app/src/main/kotlin/com/follow/clash/QuickActionActivity.kt` | `com.follow.clash.action.START/STOP/TOGGLE` 意图入口 |

**不能**把 FlClash 的整个 APK 作为独立可复用 SDK，也不建议把 Flutter 客户端整体嵌入 KidsTubeTV；Android 核心是与 FlClash 工程耦合的源代码模块，不是公开、可直接 `implementation("...")` 的普通 Maven AAR。

官方工程包含完整的按应用 VPN 选择 (`VpnService.Builder.addAllowedApplication`)、IPv6 路由和 `Core.startTun` 成功检查。可根据 GPL-3.0 许可保留注明来源后挑选并适配原生实现；这需要同步处理 Go/gomobile 生成 API、包名、原生构建和安全补丁。

## 三、当前依赖澄清

KidsTubeTV 当前引用的是 <https://github.com/oviron/libmihomo-android> 的 v0.3.13 AAR，由 `scripts/fetch-mihomo.sh` 固定 SHA-256 下载验证。

- 这个封装创建者从 **oviron/FlClash 自己维护的 FlClash fork** 提取 SDK；不是从 chen08209/FlClash 官方 repo 发布的可重用 AAR。
- GitHub Star 很少（2026-11-06 查询时仅 3），不足以代表内核是否正确/安全；真正的技术风险是 JNI 桥维护、漏洞跟进、二进制可重复构建、Android 9 TV 兼容与单维护者风险。
- 它实际内置 Go mihomo 代理内核，并非自行另写简化代理协议栈。版本与 Go 核心版本应按照 AAR 的 metadata.json / README 版本矩阵核实。
- 保留 GPL-3.0 及第三方许可与可获得的完整对应源码。

## 四、复用路径选择

### A：外部已安装 FlClash（过渡验证，最低改动）

- 官方 FlClash Android 暴露 `com.follow.clash.action.START`、`STOP`、`TOGGLE` 的 QuickAction Activity。
- 可通过显式 Intent 调用（实际包名和版本需按安装包验证）；但系统 VPN 授权、节点、按应用访问控制仍需在 FlClash 首次设置完成。
- **缺点：**需安装两个 APK；可能打开外部 UI；不能保证后台调用、状态读取或自动停止在所有 Android TV ROM 上都正确；也可能与 KidsTubeTV 内置 VPN 发生冲突。
- 使用此路径时**关闭 KidsTubeTV 内置 VPN**，并在 FlClash 里指定仅代理 `dev.kidstubetv`。两种 VPN 不能同时运行。

### B：维持当前 JNI/AAR 封装（短期 MVP）

- 已有 CI 和 APK 构建基线，可快速验证 Android 9 系统、32/64bit ABI、视频播放完整链路。
- 限制：不应拿 GitHub Star 数量作安全证明，必须在系统断网 / IPv6 / DNS、VPN 被撤销、快速启停和订阅更新场景压力验证。
- **必须**为 `KidsVpnService` + `MihomoControl` 抽取 `ProxyEngine` 抽象，以便切换底层实现。

### C：导入官方 FlClash Android core 源码并自行构建（长期推荐）

- 保留 KidsTubeTV 原生 Android TV 界面；只把 FlClash 的核心导出、VPN Service 处理与所需 adapter 引进来。
- 引入 Go、gomobile、Android SDK/NDK 的 CI 编译链，确定 commit + dependency lock，自动测核心 ABI（armv7a/arm64）。
- 保留官方许可证与来源，设置明确的上游版本检查和核心回归。
- 这不是只换 Gradle dependency：`QuickSetup` 参数、TUN options、异常清理、DNS/IPv6、`StartTun` 返回值、回调签名不同，都需要适配。

**建议：**先 B 真机验证 + 按模块封装；与此同时准备 C 的可复现源码构建；不要一边使用 A 的外部 VPN 一边启动 B/C 内置 VPN。

## 五、网络行为的关键风险

1. `VPN READY` 只表示 TUN 建立，**不等于** YouTube API/IFrame 通畅；需要在进入内容首页前验证真实 Google/YouTube 网络与节点可用。
2. 目前 SDK `TunInterface.protect(fd)` 为 Unit，不便确认保护是否成功；FlClash 官方实现的 `Core.startTun()` 和 `protect(fd)` 可返回成功标志。
3. Android 9 部分固件通过不同的 WebView 系统组件播放 IFrame；同时核对 DNS、UDP、IPv6 路由避免绕过本应用 VPN。
4. 订阅 URL token 存储需安全，APP 与 NAS/手机的配对服务必须具备局域网认证、时间限制、CSRF 防护及关闭入口策略。
5. Android 允许一次仅有一个有效 VPN；如果支持外部 FlClash 模式，就需要彻底禁用内置 VPN 启动与反向冲突。

## 六、下一阶段工程任务

1. Kotlin UI 改造（大卡片、图标栏自动收缩、播放器轻控制）并加入 TV 遥控器导航测试。
2. `ProxyEngine` 接口与 `CurrentMihomoAarEngine` 适配层，UI 仅调用 `start/stop/status/selectGroup`。
3. 对比集成官方 FlClash Go mobile 的可复现原生构建，选定许可证合规的固定版本。
4. Android TV 真机测试：Home/退出/强杀时停止、VPN revoke、DNS/IPv6、IFrame、ABI、30 分钟压力运行。
5. 手机配置配网页（**独立需求**）：仅局域网开放、短时配对码、一次性授权、节点/订阅/白名单编辑，默认完全关闭。

上述为源码核查与方案设计；**尚未替换 KidsTubeTV 当前的 AAR、运行过 FlClash 源码构建或将 HTML 布局移植到 Kotlin**。


## 七、2026-11-06 后续落地状态

本轮已向 `main` 分别提交以下原创实现：

- `MainActivity.kt`：沉浸式主推荐、三列大卡片、左键/菜单键呼出、闲置自动隐藏侧栏；VPN 底部故障条。
- `TvUi.kt`、`TvVideoCard.kt`：柔和墨蓝/海玻璃绿/暖黄焦点统一视觉（上一轮已经完成）。
- `PlayerActivity.kt`：播放全屏、方向键快进/快退、上键呼出自动隐藏控制栏、播放器底部 VPN 故障条和 OK 返回；仍逐视频二次白名单校验与观看时间限制。
- `SettingsActivity.kt`：家长设置分为「儿童内容」「网络代理」「YouTube 账号」三个大入口，仅展示选中的部分，原有高级字段仍可访问。
- `ProxyNetworkHealth.kt`：实际 HTTPS 连通性探测；两次连续失败提醒“VPN 已建立但 Google 不可达”。它**不证明** DNS/IPv6/QUIC 完全不泄漏，也不能代替 Android 锁定 VPN 模式。
- `NativeProxyCore.kt`：`NativeProxyCore` 接口与 `CurrentMihomoAarCore` 适配器，用于日后替换上游 Android 核心实现。

### 与官方 FlClash 的真实关系

当前 APK 仍采用 `oviron/libmihomo-android` v0.3.13 AAR，并非 `chen08209/FlClash` 官方 Android `android/core`/Go mobile 构建。只是内置的代理协议执行引擎同样为 Mihomo。引入 `NativeProxyCore` 是可迁移接口，**没有声称已替换为官方 FlClash**。

下一阶段迁移若选用官方实现，需要固定官方 git commit、保留 GPL 许可，构建 Go/gomobile/NDK 原生库，适配 `Core.startTun()`/回调及配置 API，再在 Android 9 TV 上专项验证 DNS、IPv6、VPN 抢占、节点重连、退出释放等。当前工程的 UI 并不依赖 FlClash Flutter。

### 验证分层

1. **已进行**：GitHub CI Kotlin 编译与纯单元测试（以对应最新 commit 的实际 Actions 状态为准）。
2. **未进行**：用户雷鸟 TV 实机网络/播放验收，包括 Android 9 系统 WebView、Google OAuth、真实 Clash 订阅、多节点、IPv6/UDP、家长 PIN 可用性、遥控器焦点兼容、VPN 中断/后台退出。
3. **未完成**：真实网络 fail-closed 保证、官方 FlClash 完整核心迁移、手机扫码/配对配置管理；这三项不可用 HTML Demo 或编译通过替代。
