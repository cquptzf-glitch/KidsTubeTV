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
