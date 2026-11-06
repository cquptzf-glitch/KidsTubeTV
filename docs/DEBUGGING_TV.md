# 雷鸟 Android TV 故障排查

## TV 内诊断

打开 **家长中心 → 网络代理 → 高级设置 → 设备与网络诊断**。

可看到：

- Android 系统版本与 API level、CPU ABI、设备机型
- System WebView 版本（IFrame 播放器会用到）
- 代理配置是否存在、VPN 服务状态
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
