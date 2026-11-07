**Android 手机 / TV 安装提示：**旧版 Pre-release 使用临时 Debug 签名，可能因证书不同导致覆盖安装报“应用未安装”。从 beta.9 起发布前必须通过 Android `apksigner` 校验；未配置稳定密钥时采用不与旧版冲突的独立测试包名。先看 [安装与签名故障排查](docs/INSTALL_AND_SIGNING.md) 和 [beta.9 Release](https://github.com/cquptzf-glitch/KidsTubeTV/releases/tag/v0.3.0-beta.9)。

# KidsTubeTV

为 Android TV（最低 **Android 9 / API 28**）开发的遥控器儿童视频应用，内置 Mihomo (Clash Meta) 应用专属 VPN，支持官方 FlClash Go 和旧版 JNI 两套后端。

[![Android TV build](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml/badge.svg)](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml)

> **当前阶段：功能版 / 待雷鸟真机验收。** GitHub Actions 可生成 Debug APK，不代表 WebView 视频、DNS/IPv6 防泄漏、所有电视固件已经通过真机测试。**不要在真实儿童使用场景中依赖尚未核验的白名单当作唯一保护措施。**

## 已实现的主要功能

- **沉浸式 Android TV UI：** 全幅主推荐背景、大封面自适应视频卡片、无常驻侧栏（左键唤出，闲置自动隐藏）、遥控器焦点高亮；播放器控制栏自动隐藏，支持上一集、下一集、暂停、快进/后退及最近观看自动续播；家长可关闭自动连播，后台回调不得自动开始播放
- **儿童保护：** 频道 ID 白名单、单视频白名单、禁止列表；YouTube Data API `madeForKids`、`embeddable` 检查，播放前再次复核
- **家长设置：** PIN（PBKDF2-SHA256 + 连续错误暂时锁定），2 分钟无操作超时与应用切后台锁定、儿童搜索开关、15–120 分钟每日观看限制、历史/收藏清除
- **极简 Clash 设置：** 默认只显示连接状态和「添加/更换订阅」「选择节点」两项；支持剪贴板 HTTPS 订阅一键导入（隐藏 token），也可在弹窗输入 URL；U 盘导入、自动更新周期、测速、流量统计等隐藏在高级功能。支持 HTTPS Clash/Mihomo YAML 和配置备份，更新配置在下次连接时生效
- **节点管理：** Mihomo Selector 代理组选节点、当前节点延迟测试、流量查询；节点选择可在下次启动时恢复
- **应用专属 VPN：** 无需 Root；首次弹系统授权，打开应用后自动启用 VPN，显式退出立刻断开，Home 键退后台约 6 秒断开，不接管其他应用路由；网络持续异常自动退避重连（最多 3 次），家长手动断开不触发自动重连
- **Google 登录（可选）：** 官方 TV OAuth 设备码授权，通过电视二维码在手机上登录，读取登录账户的 YouTube 订阅；**只有家长勾选的频道**才加入白名单
- **凭据保护：** API Key、OAuth 凭据/令牌、订阅地址使用 Android Keystore AES-GCM 存储；Mihomo YAML 放在私有目录但含有节点凭据，禁止上交版本库
- **CI/CD：** push/PR 自动测试 + Debug APK Artifact；推送 `v*` 标签时通过仓库 Secrets 构建采用官方 FlClash 核心的签名 APK、运行 Release Lint、验证签名、生成 SHA256SUMS 并发布 GitHub Release；需先设置固定签名密钥

## 最近完善（2026-11-06）

- **首页多频道推荐：** 合并家长批准的最多 5 个频道及独立批准的视频，轮流混排，每条内容仍经过儿童标记、可嵌入与白名单验证。
- **搜索配额优化：** 同一关键词的 YouTube 搜索 ID 最多缓存 10 分钟，最多保留 48 组结果；每次浏览仍重新查询和校验视频元数据，不缓存儿童审核结论。
- **订阅更新安全：** 手动更新、后台自动更新和本地 YAML 导入互斥；新订阅地址仅在配置下载成功后保存，避免旧配置与新 URL 不一致；支持家长在高级设置恢复上一份 YAML。
- **诊断：** 家长中心 → 网络代理 → 高级设置 → 设备与网络诊断，显示 Android API、CPU ABI、WebView 和 VPN 状态，可复制不含订阅令牌的报告。
- **播放恢复：** 后台切换时中断的视频审核在返回后重新请求，防止黑屏；仍需真实 Android TV 端到端测试。
- **CI：** 增加 Android Lint、单元测试、Debug APK 构建、原生 ABI 清单检查，并上传 HTML Lint 报告。

详细排查方法见 [电视调试指南](docs/DEBUGGING_TV.md)。**此项目仍以 Android TV 侧载为目标，targetSdk=32 不符合 Google Play 发布要求**；不能将 CI 成功理解为平台合规或硬件验收。

## 新用户安装

在 [Actions](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml) 中打开最新成功的 `Android TV build`，下载 **KidsTubeTV-debug-apk** Artifact 并解压安装；或从代码本地构建。

1. 在电视运行 KidsTubeTV，进入家长设置并设置 PIN。
2. 添加 YouTube Data API Key **或**后续使用 Google OAuth 授权。家长可直接粘贴 YouTube 频道地址、`@handle` 或视频分享链接，查询后确认加入白名单，无须手动查找频道 ID。
3. 首次进入家长设置会优先打开「网络代理」。选择「添加/更换 Clash 订阅」，粘贴 **HTTPS Clash/Mihomo YAML 订阅 URL**，保存后下载；或从高级设置使用 U 盘导入完整 `config.yaml`。电视上可以借助手机电视遥控器的键盘输入长链接。
4. 回到主界面，接受 Android VPN 授权，等待连接完成。
5. 通过自动收起的侧栏进入「我的频道」、分类或最近观看，选择大封面开始播放；只有通过儿童过滤的视频才进入播放器。

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

## 功能闭环台账

- [当前实现、未完成任务与静态验收](docs/FEATURE_STATUS.md)

**开发说明：** 已添加[官方 FlClash Core 独立编译与 APK 静态验证流水线](docs/FLCLASH_CORE_BUILD.md)，但常规 APK 仍使用原 Mihomo 封装；开发阶段的静态验收状态详见[功能台账](docs/FEATURE_STATUS.md)。

**官方核心独立 APK：** [FlClash 官方 Go 后端构建成功的 Actions](https://github.com/cquptzf-glitch/KidsTubeTV/actions/runs/42256808333) 已通过 Lint、单测与 APK 编译。默认 APK 仍用旧 Mihomo SDK；两个版本都未进行真实电视网络验收。

## 用手机辅助配置（不暴露订阅密钥）

- Clash 订阅：在手机复制 HTTPS 订阅链接，使用电视支持的手机遥控器/输入法将文本粘贴到 TV；家长设置中也可一键使用电视剪贴板的链接，界面只显示域名。若电视不支持跨设备剪贴板，可通过系统文件选择器导入 U 盘上的 Clash YAML。
- Google 授权：家长中心打开 Google TV 登录，用手机扫码电视上的二维码并输入页面提供的设备验证码，无需在电视上输入 Google 密码。
- 为保护可能含节点密码的订阅数据，本应用**没有开启未经验证的局域网 HTTP 订阅上传服务**。



## v0.3.0-beta.2 TV 操作与网络更新

- 家长中心改成电视遥控器友好的两栏：左边固定“儿童内容 / 网络与 VPN / YouTube 账号”，右边是可滚动的大卡片操作，折叠高级功能。
- 在网络页直接连接或重连 VPN、导入 Clash 订阅、切换节点和查看网络诊断。系统 VPN 授权过程保留短时家长会话。
- 官方 FlClash 适配器新增 Android 底层 DNS 同步，修复此前缺少 `ACCESS_NETWORK_STATE` 且未调用 `Mobile.updateDns` 的问题；实际节点连接、DNS 路由、YouTube 播放仍需电视验证。

预览版本使用 GitHub Actions 临时 Debug 签名，无法保证覆盖安装之前版本，注意备份应用配置。

## v0.3.0-beta.3 故障修复（预览）

- 修正 beta.2 官方 FlClash 适配层的系统 DNS 参数：原先传裸 IP，现在参照上游 Android NetworkObserver 传 `IPv4:53` / `[IPv6]:53`。
- VPN 网络检测异常不再阻止 Google OAuth、YouTube API、儿童视频重新审核和播放，也不会仅因单一 gstatic 探测网址失败重启 VPN；实际请求仍强制要求 App VPN 处于 READY。
- Google 登录将客户端配置放到折叠区，并给出无效客户端、授权过期、YouTube 配额的安全错误提示；页面不会重新显示已保存的 Client Secret。
- 诊断可以分别检测 Google OAuth 和 YouTube API 的 HTTPS 连通性，并显示脱敏的 VPN 错误类别；儿童首页显示尚未完成的配置步骤。

这是使用临时 GitHub Actions Debug 签名的预览版。真实 Android TV 上的原生 TUN、DNS/IPv6、OAuth 与 IFrame 行为尚需测试，不能以 Lint 与 APK 可编译代替验收。

## SmartTube 源码参考（2026-11-07）

参考 [SmartTube](https://github.com/yuliskov/SmartTube) 与 [MediaServiceCore](https://github.com/yuliskov/MediaServiceCore) 的电视登录、刷新、焦点与播放架构。本轮已自行实现带过期/失败退避的首页刷新、保留旧内容及遥控器焦点、封面尺寸缓存与失败回退，以及经过二次儿童内容审核的播放器手动重试。

**没有直接移植 SmartTube 的 YouTube 私有接口、账号凭据、完整 ExoPlayer 或第三方媒体解析器。** 项目仍使用自己的 Google Cloud TV OAuth、官方 YouTube Data API 子集和 IFrame。实现与风险详见 [SmartTube 移植评估](docs/SMARTTUBE_PORTING_REVIEW.md)。

### v0.3.0-beta.4 预览版修订

修复首页异步请求期间 VPN 突然断开后 `catalogLoading` 状态可能无法复位，导致随后 VPN 已连接也不加载视频。延续 beta.3 的 SmartTube 风格按需刷新、保留位置、播放器手动重审及 Google/VPN 故障诊断。此版本仍需要 Android TV 真机验收，使用临时 Debug 签名。


### v0.3.0-beta.5：SmartTube 架构参考第二轮

新增首页全失败错误识别与自动冷却重试、Google TV OAuth 客户端切换后的令牌失效与回调隔离、播放 iframe 实际视频 ID 绑定及返回前台二次审核，以及多个栏目间的遥控器焦点/滚动恢复。详见 [SmartTube 评估与落地](docs/SMARTTUBE_PORTING_REVIEW.md)。

仍然使用 YouTube Data API + IFrame 与官方 FlClash VPN 核心；本轮没有引入 SmartTube 非官方账号密钥、流解析及 ExoPlayer 服务层。预览版使用 CI 临时 Debug 签名，真实电视上的 VPN/Google 授权/视频播放需独立验证。

### v0.3.0-beta.6：架构、真实 API 检查与批量推荐

参考 SmartTube 服务层设计，`YouTubeRepository` 现在使用独立的 Data API 客户端和可注入 HTTP 通道；首页批量获取已批准频道元数据，再分批重新审核视频，减少重复网络请求与 quota 使用。更多已批准频道会在后续首页刷新中轮换显示，避免长期只看到前五个频道。

Google TV 账号切换和登出会撤销旧设备码/刷新请求结果；家长订阅导入也采用请求版本门控。家长网络诊断页提供**带凭据的真实 YouTube API 检查**，可区分 API 凭据不正确和纯 HTTPS 可以到达两种情况。操作与风险见 [故障排查](docs/DEBUGGING_TV.md) 和 [SmartTube 架构参考](docs/SMARTTUBE_PORTING_REVIEW.md)。

本版仍是带临时 Debug 签名的开发预览：仅静态编译、Lint 和 JVM 单测；Android TV 的真实 VPN、Google OAuth、YouTube IFrame 与儿童遥控器操作并未完成设备验收。

### v0.3.0-beta.7：Android 手机可用与精简设置

本版新增 Android 手机桌面启动入口和**竖屏/横屏自适应 UI**，电视继续使用 D-pad 导航；手机首页有可触摸菜单、可滚动侧栏、手机视频双列卡片以及两行播放控制。家长设置将三栏电视侧边导航在手机上改为顶部横向分类栏，常用功能减少为一个“下一步配置”引导；网络/儿童管理的高级操作折叠。

手机 Google 设备验证码支持复制与打开官方授权网页，不需要扫描同一块手机屏幕。家长网络诊断增加一个“开始一键检测”，依次检查 VPN、Google HTTPS 与有效的 YouTube Data API 凭据。

**Android 系统限制：** KidsTubeTV 使用应用专用 Android VPN；系统通常不能同时启动另一款 VPN，外部浏览器不通过本应用的 VPN。若手机浏览器不能直接访问 Google，请用另一台能访问的设备完成验证码授权。现阶段仅已通过 CI 静态、编译、JVM 测试，没有真实手机或电视验收报告。

详细操作：[Android 手机安装与功能验证](docs/MOBILE_VALIDATION.md)。

### v0.3.0-beta.8：Clash 订阅更新修复与设置简化

家长中心 → 网络 VPN 现在只需要在**Clash 订阅地址 · 查看 / 编辑 / 复制**中完成大部分操作。家长解锁后，原 URL 完整显示为普通文本（非密码框），可以复制、粘贴、直接修改；支持**仅保存地址**或**保存并更新**。已有订阅可直接按“↻ 更新已保存的订阅”，无需再次输入。

订阅下载针对常见 Clash 客户端 User-Agent 提供有限兼容重试，支持 4 MiB 有界 YAML、gzip 响应和相对 HTTPS 重定向；失败时保留已有可用 YAML，并显示不含订阅 token 的原因（DNS、超时、HTTP 403、返回 HTML/Base64、无效 YAML）。如果没有可用订阅/更新失败，还会直接显示“从文件导入 Clash YAML（备用）”。

**隐私说明：**只有已通过家长 PIN 的页面才展示完整 URL；复制到 Android 系统剪贴板后，其他有权限的应用仍可能读取它，完成操作后可清理剪贴板。正常网络诊断与日志不打印完整订阅链接。此版本的真实订阅网络连接仍需在手机/电视上验证。

[Clash 订阅问题排查与新操作](docs/SUBSCRIPTION_TROUBLESHOOTING.md)。
