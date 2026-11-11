# 雷鸟 Android TV 故障排查

## TV 内诊断

打开 **家长中心 → 网络与 VPN → 网络故障诊断**。无需进入高级菜单。

可看到：

- Android 系统版本与 API level、CPU ABI、设备机型
- System WebView 版本（IFrame 播放器会用到）
- 代理配置是否存在、VPN 服务状态，与 HTTPS 可达性（不暴露 DNS 地址）
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

## libmihomo-android 网络排障说明

当前版本仅调用 `libmihomo-android v0.3.13` 的 JNI 初始化与 TUN 接口；不依赖外部后端的 DNS 网络监听器。状态 READY 只表示 TUN 已建立，不代表节点通畅或 DNS/IPv6 已隔离。优先通过家长诊断的 HTTPS 检测、实际 API 请求和节点测试判断问题。

若仅有某个 `generate_204` 检测网址失败，不能直接推断 Google OAuth 与 YouTube API 也不可用；需要分别诊断 DNS/TLS、API 凭据和视频嵌入问题。

## beta.6 真实凭据诊断（家长操作）

新版本“家长中心 → 网络与 VPN → 网络故障诊断”提供 **检查 YouTube API Key / OAuth 凭据（真实接口）**。流程：

1. 确认 VPN 显示 READY，并且已提供有效 API Key 或 Google TV OAuth 登录。
2. 先使用“检查 Google OAuth / YouTube 真实 HTTPS 地址”判断纯 DNS/TLS/网络问题。
3. 再使用“检查 YouTube API Key / OAuth 凭据”，它会调用 YouTube Data API v3 `videos.list` 的 `part=id`，请求仅检查接口认证，不会将任何未批准视频加入儿童首页。该请求通常消耗 1 个 API 配额单位。
4. 若显示 quota、403、401、未启用 Data API v3 等，请检查自己的 Google Cloud 项目；不要盲目重启 VPN。失败或成功的分类结果可复制到脱敏报告中。
5. 即使此接口成功，也不证明 YouTube IFrame、安卓电视 WebView、节点 IPv6/DNS 或视频嵌入权限均可用。

不要分享 Google Client Secret、API Key、refresh token、Clash YAML 或订阅链接。若多频道首页只出现部分频道，可在菜单“我的频道”访问指定频道；首页会随刷新轮换已批准频道窗口。

## beta.7 手机测试界面

Android 手机通过通常应用图标打开 KidsTubeTV。首次使用先设置家长 PIN；家长中心的“下一步配置”会依次提示 Clash 订阅、VPN、YouTube 凭据、允许的频道。家长网络页面直接选择“一键检测网络与账号”，默认展示简短结果，“展开详细诊断”才显示原始设备信息与分项检测按钮。

设备码登录时，手机页面显示验证码并可复制；“打开授权网页”调用系统浏览器，跳转网址限制为 Google / YouTube 官方 HTTPS 域名。**外部浏览器不通过 KidsTubeTV 的应用专用 VPN**；如果 Google 网页受网络影响打不开，应另用能正常打开 Google 的设备授权。详见 [手机验证步骤](MOBILE_VALIDATION.md)。

## beta.8 Clash 订阅更新失败与地址查看

家长进入 **网络 VPN → Clash 订阅地址 · 查看 / 编辑 / 复制**，现在可以直接看到**完整 URL**，不再是密码点号。家长可复制、粘贴或编辑后选择“仅保存地址”或“保存并更新”。已保存时还可单击“↻ 更新已保存的订阅”重试。

常见错误提示说明：

| 提示 | 处理 |
| --- | --- |
| DNS 解析失败 / 连接超时 | 检查 Wi-Fi、代理节点、订阅域名；首次导入 VPN 尚不可用时可更换网络或导入本地 YAML |
| HTTP 401 / 403 | 订阅令牌可能失效；检查服务商后台是否提供 Clash / Clash Meta 专用地址；当前客户端会针对可能的 UA 不兼容重试一次 |
| HTTP 404 / 429 | 链接失效 / 请求过于频繁，重新复制地址或稍后重试 |
| 返回登录网页 / Base64 | 服务器返回的不是 Clash YAML；选择服务商的 Clash/Mihomo 格式订阅，不能把通用 Base64 节点当作配置 |
| YAML 无效或拒绝危险接口 | 服务商配置格式错误，或试图开放 LAN/远程管理接口；出于儿童安全考虑不会跳过校验 |
| 文件超过 4 MiB | 超出有界下载/解析上限，联系服务商获取精简 Clash 配置 |

手动或自动更新失败不会覆盖当前可用 YAML。若在已有 VPN 下下载成功，可选择立即重启应用 VPN 以应用新配置。如果订阅首次无法下载，可通过“从文件导入 Clash YAML（备用）”使用手机的下载目录或电视 U 盘；该操作同样会验证文件内容。

**安全：**为了方便核对，家长 PIN 后的输入框会完整展示带 token 的链接，复制时也会进入系统剪贴板；不要截图/外发包含 token 的 URL。程序的普通诊断报告仍不包含完整 URL。
