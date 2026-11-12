# KidsTubeTV 功能状态与静态验收台账

更新：2026-11-11。仅描述源码和 CI 配置，不将编译成功等同于 Android TV 真机通过。

## 唯一代理后端

- **选定：** [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android) **v0.3.13**。
- **制品：** `app/libs/libmihomo-android-v0.3.13.aar`，由 `scripts/fetch-mihomo.sh` 或 PowerShell 脚本下载并验证固定 SHA-256。
- **适配器：** `proxy/LibMihomoCore.kt` 和 `proxy/LibMihomoProxyControl.kt` 位于 `src/main/java`。Debug、Preview、Release 使用同一 JNI 后端，不再分双源码集。
- **网络：** Android `VpnService` 建立应用专属 TUN；`Clash.quickSetup/startTUN/stopTun` 和 `Clash.invokeAction` 接口保持；保留节点选择、订阅 YAML 下载、配置缓存和回滚。

## 2026-11-12：设置 UI 双端信息架构

- 五分区 `SettingsDestination` 路由：概览、应用网络、YouTube 账号、儿童内容、家长与安全。原有 `EXTRA_SECTION=1` 网络入口保留；其他分区使用新的索引。
- 电视用左侧固定导航与 `wireTvFocus`，手机采用顶栏返回与概览卡片，旋转时保存当前分区。
- `SubscriptionEditorActivity` 改成独立全页输入，支持复制/粘贴、TV 初始焦点、手机底部主按钮；使用 `FLAG_SECURE`，并且不将订阅 token 放到 Intent。
- PIN / 清除观看历史迁到「家长与安全」；原有儿童白名单、Google 授权、节点选择及诊断功能继续使用现有实现。
- `SettingsNavigationPolicyTest` 覆盖历史网络入口索引、错误索引和返回路线。自动化测试不会代替 D-pad、触屏和系统键盘的真机手动验收。

## 2026-11-12：应用专用 VPN 及极简家长页

- `KidsVpnService.appOnlyBuilder()` 使用 `AppOnlyVpnScope.allowedPackage(packageName)`，固定只添加当前应用包名，避免空 allowlist 导致全设备走 VPN；JVM 测试验证正式/预览包名和输入有效性。
- 家长网络页由多项常驻操作改为 **「连接应用网络」和「添加/更换订阅」**；节点选择、更新、文件导入、诊断等隐藏在可展开高级区。
- 首次保存链接后直接下载，成功后可以在家长 PIN 会话有效时请求系统 VPN 授权并建立连接；家长手动暂停期间不会自动重连。
- **Android 平台限制：** 不在名单中的应用不走本 VPN，但系统 VPN 服务本身通常独占；打开 KidsTubeTV VPN 可能替换其他 VPN，且不代表已经做过真机 DNS/IPv6 防泄漏验证。

## 现有代码功能（仍需设备验收）

| 领域 | 主要实现 | 静态验证边界 |
| --- | --- | --- |
| TV/手机响应式首页与遥控器 | `MainActivity`、`TvVideoCard`、`UiDevice` | 焦点及视觉需设备验证 |
| 儿童准入、家长白名单、重新审核 | `AdmissionPolicy`、`YouTubeRepository`、`PlayerActivity` | 真实 Data API/IFrame 待验证 |
| 视频续播、收藏、观看时长 | `WatchHistory`、`WatchProgressPolicy` | 播放事件和状态机需回归 |
| Google OAuth 和频道导入 | `GoogleTvAuth`、`GoogleOAuthHttpClient`、`SecurePrefs` | 真实账号授权待验证 |
| Clash YAML 订阅、校验、回滚 | `SubscriptionClient`、`ProfileFileStore` | 需真实网络/配置验证 |
| VPN 启停、故障提示、探测 | `KidsVpnService`、`VpnSessionGate`、`ProxyNetworkHealth` | TUN/DNS/IPv6 必须真机验证 |
| 签名安装与 APK 构建 | `android.yml`、`preview-release.yml`、`release.yml` | CI 与设备安装仍须检查 |

## 2026-11-11 后续开发：并发与 API 传输加固

- **订阅更新提交：** `SubscriptionProfileRevision` + `SettingsStore.installFetchedSubscription` 将旧请求结果隔离。家长切换 URL、导入本地文件和恢复备份后，过期响应不会覆盖现有 YAML；成功更新使同一版本的其它结果失效。失败错误仅在原 URL 和配置版本仍有效时记录。
- **自动更新：** `SubscriptionUpdater` 下载完成后复查 VPN READY 和 URL/版本，符合条件才安装；不会直接热替换运行中的原生核心。
- **YouTube API：** `GoogleYouTubeHttpTransport` 固定 Google HTTPS 主机及允许的 Data API 路径，不跟随重定向、请求头携带凭据、请求前复查 VPN；成功/错误 JSON 读取量分别限制为 2 MiB / 32 KiB。仅解析已知错误码。
- **新增 JVM 测试：** `SubscriptionProfileRevisionTest`、`GoogleYouTubeHttpTransportTest`。必须以本次 commit 的真实 CI 结果确认编译、Lint 和单测状态；尚未运行真机。

## 当前风险与开发优先级

1. **P0：** Android 9 电视上的端到端 VPN、真实节点连通、DNS/IPv6、系统 VPN 回收以及 `protect(fd)` 失败语义。
2. **P0：** Google OAuth 授权、YouTube Data API 和 IFrame 播放在实际设备上的联调。
3. **P1：** 播放器切换、背景事件和网络中断时的状态机。
4. **P1：** 生产版固定签名证书、versionCode 递增、Debug/Preview/Release 真机安装。
5. **P2：** 仅依赖 YouTube API 元信息无法替代家长监督与真实的儿童内容审核。

本轮移除另一套原生核心、专属构建和专用底层 DNS 监听，CI 统一获取 SHA-256 锁定的 AAR。历史 beta.9 APK 不是本轮代码生成的制品。

**这是静态迁移台账，不声称新后端已经通过 CI 或设备验收。**
