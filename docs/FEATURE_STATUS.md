# KidsTubeTV 功能状态与静态验收台账

更新：2026-11-11。仅描述源码和 CI 配置，不将编译成功等同于 Android TV 真机通过。

## 唯一代理后端

- **选定：** [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android) **v0.3.13**。
- **制品：** `app/libs/libmihomo-android-v0.3.13.aar`，由 `scripts/fetch-mihomo.sh` 或 PowerShell 脚本下载并验证固定 SHA-256。
- **适配器：** `proxy/LibMihomoCore.kt` 和 `proxy/LibMihomoProxyControl.kt` 位于 `src/main/java`。Debug、Preview、Release 使用同一 JNI 后端，不再分双源码集。
- **网络：** Android `VpnService` 建立应用专属 TUN；`Clash.quickSetup/startTUN/stopTun` 和 `Clash.invokeAction` 接口保持；保留节点选择、订阅 YAML 下载、配置缓存和回滚。

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

## 当前风险与开发优先级

1. **P0：** Android 9 电视上的端到端 VPN、真实节点连通、DNS/IPv6、系统 VPN 回收以及 `protect(fd)` 失败语义。
2. **P0：** Google OAuth 授权、YouTube Data API 和 IFrame 播放在实际设备上的联调。
3. **P1：** 播放器切换、背景事件和网络中断时的状态机。
4. **P1：** 生产版固定签名证书、versionCode 递增、Debug/Preview/Release 真机安装。
5. **P2：** 仅依赖 YouTube API 元信息无法替代家长监督与真实的儿童内容审核。

本轮移除另一套原生核心、专属构建和专用底层 DNS 监听，CI 统一获取 SHA-256 锁定的 AAR。历史 beta.9 APK 不是本轮代码生成的制品。

**这是静态迁移台账，不声称新后端已经通过 CI 或设备验收。**
