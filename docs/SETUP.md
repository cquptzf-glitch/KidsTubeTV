# KidsTubeTV · 开发、配置和真机安装

## 1. 当前基线

- Android 9 / API 28 及以上；目标机型：雷鸟 43 英寸 Android TV（需实际核实 `adb shell getprop ro.build.version.sdk`）
- appId: `dev.kidstubetv`
- Kotlin 2.2.20、AGP 8.13.2、Gradle 8.13、JDK 17、compileSdk 36、targetSdk 32
- YouTube IFrame：`com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0`
- Mihomo AAR: `libmihomo-android-v0.3.13.aar`，固定 sha256: `c6c2787e5dd888a8f9193501506dfa3a4f8a38fbd51adefccf67d84d0c8e0fb5`
- JVM 和 Kotlin 字节码目标 17 是开发工具要求，与 Android API 28 没有等同关系。

## 2. 构建

Windows 安装 Android Studio + SDK Platform 36 + JDK 17。使用 Gradle 8.13。

```powershell
git clone https://github.com/cquptzf-glitch/KidsTubeTV.git
cd KidsTubeTV
powershell -ExecutionPolicy Bypass -File scripts/fetch-mihomo.ps1
gradle :app:testDebugUnitTest :app:assembleDebug
```

Linux/macOS: `bash scripts/fetch-mihomo.sh && gradle :app:testDebugUnitTest :app:assembleDebug`。

AAR 下载脚本校验固定 SHA-256；未经确认不要更改版本和哈希。GitHub Actions 会上传 `KidsTubeTV-debug-apk` 构建产物，只有成功运行后才是可安装 APK。

## 3. 调试雷鸟 TV

确认 TV 与开发电脑同一 LAN。在电视开发者选项打开 ADB 或网络调试（具体因固件而异）。

```bash
adb connect 192.168.1.50:5555
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.kidstubetv/.ui.MainActivity
adb logcat -s AndroidRuntime chromium
```

这里的 IP 仅是示例。电视网络调试可能是不同端口；务必以实际为准。

## 4. 首次配置

1. 启动 KidsTubeTV，进入「家长设置」，创建四位及以上 PIN。
2. 在 Google Cloud Console 启用 YouTube Data API v3，获取只授权此 API 的 Key。请留意将 Key 放在 APK/设备内不是真正的保密方案，不要共享。
3. 填写家长批准的频道 ID（精确 `UC...`，不是频道名）；也可以填写精确视频 ID。
4. 准备能被 libmihomo 识别的完整 YAML，含代理节点/提供方、代理组等，将其复制到 U 盘。导入本地 YAML。
5. 保存设置、返回首页、点击系统 VPN「允许」。
6. 状态显示 `READY` 后选择类别；视频通过 YouTube Data API 再次检查才允许播放。
7. Home/切换离开全部 KidsTubeTV 活动约 6 秒后自动断开；应用界面内「退出」立即请求断开。

## 5. 必须验证的异常路径

- API Key 无效、配额用完、搜索为空、无儿童标记、视频不可嵌入
- 频道 ID 伪造、白名单为空、视频 ID 被黑名单拒绝
- VPN 未授权、核心库加载失败、YAML 不合法、TUN 建立失败
- 32 位 ARM / 64 位 ARM native library 分别加载
- 电视原生 WebView 过旧，IFrame 不能播放 / 视频加载超时
- Android TV 遥控器 D-pad、返回、焦点和 10 秒快进
- Home → 6 秒停代理 → 返回应用；Settings 页内跳转不误停
- 其他 VPN 已在运行时的冲突处理
- 无可用网络时不允许直接绕过 VPN

## 6. 当前未完成

- 真机稳定性测试；端到端 DNS、IPv6 与 WebView 网络策略验证
- TUN FD 异常路径（包括启动中断）的所有权诊断与资源泄漏专项测试
- 配置文件校验、节点健康检查、订阅更新和失败重试
- API 结果缓存、页面缩略图墙、WebView 多固件兼容、视频播放错误重试
- PIN 防暴力猜测、应用级退出锁、系统级家长控制
- 依赖许可证 SBOM 与最终发布前的安全审计
