# KidsTubeTV

为 Android TV（最低 **Android 9 / API 28**）开发的遥控器儿童视频应用，内置 Mihomo (Clash Meta) 应用专属 VPN。

[![Android TV build](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml/badge.svg)](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml)

> **当前阶段：功能版 / 待雷鸟真机验收。** GitHub Actions 可生成 Debug APK，不代表 WebView 视频、DNS/IPv6 防泄漏、所有电视固件已经通过真机测试。**不要在真实儿童使用场景中依赖尚未核验的白名单当作唯一保护措施。**

## 已实现的主要功能

- **沉浸式 Android TV UI：** 全幅主推荐背景、大封面自适应视频卡片、无常驻侧栏（左键唤出，闲置自动隐藏）、遥控器焦点高亮；播放器控制栏自动隐藏，支持上一集、下一集、暂停和快进/后退
- **儿童保护：** 频道 ID 白名单、单视频白名单、禁止列表；YouTube Data API `madeForKids`、`embeddable` 检查，播放前再次复核
- **家长设置：** PIN（PBKDF2-SHA256 + 连续错误暂时锁定），儿童搜索开关、15–120 分钟每日观看限制、历史/收藏清除
- **极简 Clash 设置：** 默认只显示连接状态和「添加/更换订阅」「选择节点」两项；订阅 URL 在弹窗中输入，U 盘导入、自动更新周期、测速、流量统计等隐藏在高级功能。支持 HTTPS Clash/Mihomo YAML 和配置备份，更新配置在下次连接时生效
- **节点管理：** Mihomo Selector 代理组选节点、当前节点延迟测试、流量查询
- **应用专属 VPN：** 无需 Root；首次弹系统授权，打开应用后自动启用 VPN，显式退出立刻断开，Home 键退后台约 6 秒断开，不接管其他应用路由
- **Google 登录（可选）：** 官方 TV OAuth 设备码授权，通过手机登录，读取登录账户的 YouTube 订阅；**只有家长勾选的频道**才加入白名单
- **凭据保护：** API Key、OAuth 凭据/令牌、订阅地址使用 Android Keystore AES-GCM 存储；Mihomo YAML 放在私有目录但含有节点凭据，禁止上交版本库
- **CI/CD：** push/PR 自动测试 + Debug APK Artifact；推送 `v*` 标签时使用仓库 Secrets 构建签名 APK、生成 SHA256SUMS 并发布 GitHub Release

## 新用户安装

在 [Actions](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml) 中打开最新成功的 `Android TV build`，下载 **KidsTubeTV-debug-apk** Artifact 并解压安装；或从代码本地构建。

1. 在电视运行 KidsTubeTV，进入家长设置并设置 PIN。
2. 添加 YouTube Data API Key **或**后续使用 Google OAuth 授权，并填写精确批准的频道 ID（以 UC 开头，24 位），也可填写单视频 ID。
3. 首次进入家长设置会优先打开「网络代理」。选择「添加/更换 Clash 订阅」，粘贴 **HTTPS Clash/Mihomo YAML 订阅 URL**，保存后下载；或从高级设置使用 U 盘导入完整 `config.yaml`。电视上可以借助手机电视遥控器的键盘输入长链接。
4. 回到主界面，接受 Android VPN 授权，等待连接完成。
5. 选择“批准频道”或分类，开始观看；只有通过儿童过滤的视频才进入播放器。

**关于订阅：** 支持有效的 HTTPS YAML。**Base64 节点列表、vmess://、ss://、订阅转换服务响应并不自动转换为 Mihomo YAML**；这类地址请先通过可信工具转为合法的 Clash/Mihomo YAML。初次订阅下载本身需要可访问订阅服务器的网络，如现有 TV 网络不能访问，请先从 U 盘导入已下载的 YAML。

**关于 Google：** 可以选择公共 API Key（无需登录），或者使用 Google OAuth 令牌访问 YouTube Data API；如需导入自己 YouTube 账号订阅，可以使用家长设置里的可选 Google TV OAuth。需在 Google Cloud 创建 `TVs and Limited Input devices` 类型的 OAuth 客户端。此登录独立于电视系统 Google Account，不同步 YouTube 历史、点赞或官方 Kids 资料，详见 [Google 登录](docs/GOOGLE_LOGIN.md)。

## 技术栈

- Kotlin 2.2.20 / Android Views / minSdk 28 / compileSdk 36
- Gradle 8.13 / Android Gradle Plugin 8.13.2 / JDK 17
- [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player) 13.0.0（IFrame）
- [libmihomo-android](https://github.com/oviron/libmihomo-android) v0.3.13（本地 AAR，由校验 SHA256 的脚本下载），Mihomo
- [Glide](https://github.com/bumptech/glide) 4.16.0（视频封面）

## 文档

- [安装与调试](docs/SETUP.md)
- [架构、生命周期及安全边界](docs/ARCHITECTURE.md)
- [Google 电视 OAuth 授权](docs/GOOGLE_LOGIN.md)
- [CI/CD、签名配置和版本发布](docs/CI_CD.md)
- [Android 9 电视验收清单](docs/TV_TEST_PLAN.md)

## 许可与来源

本仓库原创部分采用 GPL-3.0-only。**并未复制 BabyTube 无开放许可证的源码**；参考过它的公开需求设计。Mihomo Android AAR 采用 GPL-3.0，分发时应满足 GPL 和其他第三方依赖许可。详情参阅 [LICENSE](LICENSE)。公开仓库里不要保存代理链接、API Key、OAuth 令牌、Google Client Secret 或签名密钥。
