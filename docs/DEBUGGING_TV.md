# 雷鸟 Android TV 故障排查

## TV 内诊断

打开 **家长中心 → 网络与 VPN → 网络故障诊断**。无需进入高级菜单。

可看到：

- Android 系统版本与 API level、CPU ABI、设备机型
- System WebView 版本（IFrame 播放器会用到）
- 代理配置是否存在、VPN 服务状态，以及物理网络 DNS 数量（不会暴露 DNS 地址）
- Google HTTPS 探测状态（UNKNOWN / ONLINE / UNREACHABLE）
- 家长批准频道数量（不列出具体频道或账号）

点「复制不含密钥的诊断报告」可粘贴到 Issue 或给开发者。**报告不含订阅 URL、Google 凭据、YAML 配置、PIN 和观看历史。**

## 常见情况

| 症状 | 建议操作 |
|---|---|
| Android API 低于 28 | 当前 APK 不兼容；先核实电视固件版本 |
| APP 启动后 VPN 权限弹窗 | 需家长同意 Android 系统 VPN 授权 |
| VPN 状态 READY，但 HTTPS UNREACHABLE | 先查节点及 DNS；也可能是仅 Google 检测域名不可达 |
| YouTube API 返回 403 | 检查 Google API Key/OAuth、API 是否启用与配额 |
| 视频列表有封面但播放器黑屏 | 记录 WebView 版本、网络与 DRM/嵌入错误 |
| VPN 连接后其他 VPN 被停用 | Android 系统通常只允许一个活动 VPN；关闭其他 VPN |
| 换订阅后节点未变化 | 新 YAML 设计上在下一次连接 VPN 时生效 |

## ADB 调试

```bash
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb logcat -s AndroidRuntime chromium
```

如提交 Issue，请**不要**附上包含 Clash 节点密码、订阅 token、Google OAuth 令牌、个人频道/视频历史的完整日志。

> 构建成功不代表已通过物理设备上的 DNS、IPv6 和 WebView 测试；当前网络检测仅用于诊断可达性。

## 本轮 FlClash DNS 修复说明

以前官方 FlClash 预览版未同步 Android 底层 Wi-Fi/以太网 DNS，也缺少 `ACCESS_NETWORK_STATE` 权限；在 `system` DNS 模式下可能导致隧道虽然启动，但域名请求失败。当前版增加系统网络监听，读取非 VPN 的物理网络 DNS，并调用 `Mobile.updateDns` 同步给 FlClash 内核，停止时移除监听。网络诊断显示“底层网络 DNS 数量”，为 0 时应先检查电视 Wi-Fi/网线、DNS 配置与系统权限。

该修复属于代码层面的推断与检查，**不代表所有 VPN 问题均已修好**。如果新 APK 仍不能联网，建议复制家长诊断报告、描述状态变化与电视系统版本，后续结合真实设备和节点日志分析。

## beta.3 联动故障排查（2026-11-07）

beta.2 的官方 FlClash Android 适配器将 Wi-Fi DNS 以纯 IP 传入 `Mobile.updateDns`，但上游 FlClash `NetworkObserver` 实际使用带端口的 `IPv4:53` / `[IPv6]:53` 字符串。beta.3 已参照上游修正，并添加 JVM 格式测试；同时单个 `generate_204` 检测失败不再直接阻止真实 Google API、OAuth 和播放请求，也不会自动重启 VPN。

在家长中心 → 网络与 VPN → 网络故障诊断，先确认以下信息：

1. VPN 是 OFF、STARTING、READY 还是 ERROR；若 ERROR，查看带 `VPN_` 前缀的失败类别。
2. 底层网络 DNS 数量是否大于 0。为 0 时检查电视物理 Wi-Fi/以太网、网关 DNS。
3. 点击“检查 Google OAuth / YouTube 真实 HTTPS 地址”。这是**不带凭据的网络层检测**，收到 HTTP 400/401/403/405 也可以表示 HTTPS/TLS 已到达服务端，不能据此判定 OAuth/API 认证成功。
4. 如果 Google HTTPS 到达、OAuth 仍报 `invalid_client`，检查 Google Cloud 项目是否启用 YouTube Data API v3、客户端类型是否为 TV、Client ID/Secret 是否来自同一客户端。
5. 如果 YouTube API 返回 `quotaExceeded`、`insufficientPermissions` 等，按照应用给出的 API 错误类别检查，不要不断重启 VPN。

**请保留或备份 TV 上的 Clash 配置；临时 Debug 签名变化可能导致安装包无法直接覆盖升级。** 脱敏报告可用来定位，但不要提交 YAML、订阅 URL、API Key、OAuth token 或 PIN。

## beta.6 真实凭据诊断（家长操作）

新版本“家长中心 → 网络与 VPN → 网络故障诊断”提供 **检查 YouTube API Key / OAuth 凭据（真实接口）**。流程：

1. 确认 VPN 显示 READY，并且已提供有效 API Key 或 Google TV OAuth 登录。
2. 先使用“检查 Google OAuth / YouTube 真实 HTTPS 地址”判断纯 DNS/TLS/网络问题。
3. 再使用“检查 YouTube API Key / OAuth 凭据”，它会调用 YouTube Data API v3 `videos.list` 的 `part=id`，请求仅检查接口认证，不会将任何未批准视频加入儿童首页。该请求通常消耗 1 个 API 配额单位。
4. 若显示 quota、403、401、未启用 Data API v3 等，请检查自己的 Google Cloud 项目；不要盲目重启 VPN。失败或成功的分类结果可复制到脱敏报告中。
5. 即使此接口成功，也不证明 YouTube IFrame、安卓电视 WebView、节点 IPv6/DNS 或视频嵌入权限均可用。

不要分享 Google Client Secret、API Key、refresh token、Clash YAML 或订阅链接。若多频道首页只出现部分频道，可在菜单“我的频道”访问指定频道；首页会随刷新轮换已批准频道窗口。
