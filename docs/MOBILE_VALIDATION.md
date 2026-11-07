## beta.9 安装说明

当前下载安装失败优先查 [安装与签名排查](INSTALL_AND_SIGNING.md)。beta.8 与更早的 GitHub Actions Debug 包可能无法相互覆盖；beta.9 默认采用独立 `dev.kidstubetv.preview.b9` 测试包名并在发布流水线检查 APK v2 签名。它与旧版并行安装，旧配置不自动迁移。

# KidsTubeTV · Android 手机安装与功能验证（beta.7）

> 这是**开发预览版验证手册**，不是已执行的手机测试报告。先在 Android 手机复现界面和网络问题，再与 Android TV 对比。最低 Android 9（API 28）。

## 1. 安装前须知

- 从 [v0.3.0-beta.8 Release](https://github.com/cquptzf-glitch/KidsTubeTV/releases/tag/v0.3.0-beta.8) 获取带 `FlClash` 标识的预览 APK；安装后应该在普通手机应用列表里看到 KidsTubeTV。
- APK 使用 GitHub Actions 临时 Debug 签名，不保证与 beta.6 可覆盖更新。Android 若提示签名不匹配，先**在自己的安全位置记录 Clash 订阅、Google Cloud 凭据配置、家长批准列表**，再卸载旧版。应用设置 `allowBackup=false`，不要假设系统备份可恢复内部配置，也不要将密钥发给开发者或聊天。
- Android 同时通常只允许一个 VPN：启用 KidsTubeTV VPN 时可能会断开另一款 VPN；且只有 KidsTubeTV 自身 App 被添加到 VPN 路由，手机外部浏览器**不经过**该应用的 VPN。仍需通过自己的网络确保浏览器可访问 Google。

## 2. 五步首次配置

1. 打开首页左上角 **☰ 菜单 → 家长设置**，创建 4～12 位家长 PIN。儿童页本身不显示账号密码。
2. 在家长中心的**下一步配置**卡片粘贴有效 HTTPS Clash / Mihomo YAML 订阅；可以从剪贴板识别，无需用小屏幕手动编辑 YAML。
3. 轻触 **连接 VPN** 并同意 Android 系统提示。VPN 显示 READY 后才可请求 Google/YouTube；无直连降级。
4. 在 **YouTube** 页面二选一：输入你自己的 YouTube Data API v3 Key；或配置有效 Google TV OAuth 客户端，复制设备验证码，使用能访问 Google 的手机浏览器/另一台设备完成授权。首次 Google 登录配置仍需合法 Client ID/Secret。
5. 在 **儿童内容** 页面粘贴 YouTube 频道分享链接并确认家长白名单。不能因为搜索到视频就绕过 `madeForKids` / `embeddable` 审核。

家长中心的前三个顶栏为 **儿童内容 / 网络 VPN / YouTube**，其它选项默认收纳在“更多管理”中。手机也可通过 Android 系统返回操作退出页面。

## 3. 快速验证矩阵

| 测试场景 | 操作 | 预期 |
| --- | --- | --- |
| 手机竖屏 | 启动首页，打开 ☰ 菜单 | 顶部菜单可点，侧栏可滚动，没有被截断的固定电视侧栏 |
| 手机横屏 | 旋转设备 | 首页卡片与设置页随宽度重排，不会强制横屏或无法返回 |
| 首页 / 视频 | 已有批准视频后浏览首页 | 约两列手机卡片，触摸可打开已审核视频 |
| 播放触控 | 在播放页轻触画面、暂停/下一集 | 控制条常驻并可触摸；下一集也必须重新审核 |
| Google OAuth | 在 YouTube 授权页获取验证码 | 手机可复制验证码和打开官方 HTTPS 授权网址，旧会话不可覆盖新状态 |
| 网络 / API | 家长中心 → 网络 VPN → 一键检测网络与账号 | 将“VPN 已启动”“Google HTTPS 可达”“API 凭据有效”分开报告；详细报告默认折叠 |
| VPN 断开 | 手动断开 VPN，重新进入播放 | Google 请求应被阻断、播放器暂停，并提示重新连接；**不得回退直连** |
| 白名单 | 尝试播放未批准或未标记儿童内容的视频 | 不应开始播放；添加批准频道后才有符合条件的结果 |
| 家长锁 | PIN 失效后再尝试操作设置或授权 | 应退出家长页面，要求重新 PIN 验证 |
| 电视回归 | 在 Android TV 使用 D-pad 打开菜单和播放器 | 原有焦点大卡片、遥控器操作和自动隐藏控制条保持 |

## 4. 有线 ADB 方式（可选）

```bash
adb install -r KidsTubeTV-v0.3.0-beta.8-FlClash-PREVIEW.apk
adb shell am start -n dev.kidstubetv/.ui.MainActivity
adb logcat -d -s AndroidRuntime:E
```

如果 `adb install -r` 因签名不同报错，需要先按第 1 节准备好个人配置，再自行卸载重新安装，**不要直接抹掉重要设置**。运行时异常的日志可能包含设备环境信息，发送前请手工检查脱敏。

## 5. 验证边界

- 当前 CI 只做 Lint、JVM 单测、Debug APK 打包与原生 FlClash 核心构建。没有连接你的真实手机或电视，也没有执行真实 VPN / OAuth / IFrame 的端到端测试。
- “HTTPS 可达”不等于“API Key 通过”，而“API Key 通过”仍不等于“视频嵌入正常播放”。诊断逐项提示，别把某一步成功当作整体完成。
- 如果手机/电视的 Google 网页不可达，先检查物理网络、系统 VPN 互斥、Android WebView 与 TV Google 授权客户端；不建议复制 SmartTube 第三方 OAuth Client Secret。

## beta.8 订阅下载失败专项验证

1. 网络 VPN 页面，打开“Clash 订阅地址 · 查看 / 编辑 / 复制”，确认已保存地址为**普通文本**，可以用复制按钮或长按选择；不要向开发者透露订阅 token。
2. 粘贴自己可用的 HTTPS Clash/Mihomo 地址，按“保存并更新”。下载成功后检查提示；原 VPN 若在运行，选择“立即应用”才会重连。
3. 按“↻ 更新已保存的订阅”，确认无需再次输入。模拟断网/使用不可达域名，确认出现 DNS/超时分类并且之前已保存的 YAML 未被覆盖。
4. 若更新失败，检查页面是否显现“从文件导入 Clash YAML（备用）”，并从手机文件管理器的下载目录打开自己的 YAML；它也需要符合本程序安全校验。
5. 用服务商官方客户端比较订阅下载可用性，核对地址是否已经过期或采用非 Clash 格式；不要为了显示成功而关闭危险的配置校验。
