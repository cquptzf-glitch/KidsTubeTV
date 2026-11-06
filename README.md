# KidsTubeTV

专为 Android TV（最低 **Android 9 / API 28**）制作的儿童 YouTube 播放应用，采用原创 Kotlin Android TV 代码，以及独立的 Mihomo Android VPN 内核。支持**应用启动自动开启代理、回到电视桌面自动断开、仅本应用走 VPN**。

> **状态：MVP 初始实现 / 需要 CI 与真机测试。** 请勿在验证家长白名单与播放校验之前让儿童单独使用。没有内置可用代理、订阅或 YouTube API 密钥。

## 当前功能

- 原生 TV 遥控器导航、分类浏览、IFrame 视频播放、上下一集、快进后退
- YouTube Data API v3 搜索和 `videos.list` 复核，要求 `madeForKids` + `embeddable`
- 频道 ID 白名单、精确视频白名单、显式黑名单；列表和每次播放均检查
- 家长 PIN（PBKDF2-SHA256），两分钟内存解锁会话，敏感设置存本地
- 导入完整 Mihomo YAML 配置，固定版本 Android JNI/AAR 桥接
- Android `VpnService` + `addAllowedApplication(packageName)`，仅 KidsTubeTV 使用 VPN
- 首次启动进行系统 VPN 授权；应用全部退到后台 6 秒后断开，明确退出立即断开
- GitHub Actions 单元测试与 Debug APK 构建（无需将密钥上传至 GitHub）

## 立即开始

**Windows PowerShell：**

```powershell
git clone https://github.com/cquptzf-glitch/KidsTubeTV.git
cd KidsTubeTV
./scripts/fetch-mihomo.ps1
gradle :app:testDebugUnitTest :app:assembleDebug
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

没有 Gradle 命令时，可用 Android Studio 打开工程并构建。Gradle **8.13**、JDK **17**、Android SDK **36**。详见 [开发和安装指南](docs/SETUP.md)。

**初次配置**：进入「家长设置」，设置 PIN，填写 YouTube Data API Key，输入批准频道的 `UC...` 频道 ID，导入合法的 Mihomo YAML 文件。然后回到首页，允许系统 VPN 请求，点击分类测试播放。

## 架构

`MainActivity / PlayerActivity` → `YouTubeRepository / AdmissionPolicy` → `VpnService` + `libmihomo-android`。Android TV 无需 Root，其他 App 不会随 KidsTubeTV 走代理。

详细设计参见 [架构与验收](docs/ARCHITECTURE.md)。

## 当前限制

- UI 为原创 Android Views 实现的 TV 版 MVP，不是原 BabyTube 的 Compose UI，首版未实现缩略图墙、观看时间限制、频道管理器或跨端同步。
- 视频 IFrame 依赖雷鸟固件中的 Android System WebView，部分嵌入视频可能不可播放。
- 当前 TUN 为 IPv4 测试配置；DNS/IPv6、防回退直连和不同 Mihomo YAML 的兼容性仍需在真机核验。
- 应用启动代理需首次系统授权。Home 后断开有 6 秒宽限期，系统强杀进程前不保证回调。
- 一次仅允许 Android 当前 VPN 用户配置中有一个活动 VPN；可能与电视现有 FlClash 冲突。
- 本项目没有现成可用的节点、订阅或共享 API 密钥。API/视频访问需遵循服务政策和当地法律。
- 旧版电视的 ABI、VPN、WebView、授权弹窗可能存在机型差异。**未宣称已通过雷鸟真机测试**。

## 来源、许可与隐私

此仓库的 App 业务代码为独立原创实现，**并未复制 [BabyTube](https://github.com/amalskr/BabyTube) 未授权的源码**。仅参考其公共功能需求；BabyTube README 标注 All rights reserved，署名不等于衍生开发许可。

包含的 Mihomo Android bridge 来源于 [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android)，固定使用 v0.3.13，采用 GPL-3.0 许可证。此仓库原创源码按 **GPL-3.0-only** 授权；最终分发 APK 时须保留相应许可及第三方声明，并遵守 YouTube 播放器库等依赖的许可证。许可证见 [LICENSE](LICENSE)。

**不要提交** `local.properties`、代理 YAML、订阅 URL、Google API 密钥或真机调试日志。
