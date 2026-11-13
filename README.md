**Android 手机 / TV 安装提示：**旧版 Pre-release 使用临时 Debug 签名，可能因证书不同导致覆盖安装报“应用未安装”。从 beta.9 起发布前必须通过 Android `apksigner` 校验；未配置稳定密钥时采用不与旧版冲突的独立测试包名。先看 [安装与签名故障排查](docs/INSTALL_AND_SIGNING.md)。

# KidsTubeTV

为 Android TV（最低 **Android 9 / API 28**）开发的遥控器儿童视频应用，内置 Mihomo (Clash Meta) 应用专属 VPN，仅使用 `oviron/libmihomo-android v0.3.13` 单一 JNI 后端。

[![Android TV build](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml/badge.svg)](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml)

> **当前阶段：功能版 / 待雷鸟真机验收。** GitHub Actions 可生成 Debug APK，不代表 WebView 视频、DNS/IPv6 防泄漏、所有电视固件已经通过真机测试。**不要在真实儿童使用场景中依赖尚未核验的白名单当作唯一保护措施。**

## 已实现的主要功能

- **沉浸式 Android TV UI：** 全幅主推荐背景、大封面自适应视频卡片、无常驻侧栏（左键唤出，闲置自动隐藏）、遥控器焦点高亮；播放器控制栏自动隐藏，支持上一集、下一集、暂停、快进/后退及最近观看自动续播；家长可关闭自动连播，后台回调不得自动开始播放
- **儿童保护：** 频道 ID 白名单、单视频白名单、禁止列表；YouTube Data API `madeForKids`、`embeddable` 检查，播放前再次复核
- **电视 + 手机双端家长设置：** 五个分区（概览 / 应用网络 / YouTube 账号 / 儿童内容 / 家长与安全）；电视固定遥控器焦点侧栏，手机概览卡片与单列详情/返回导航；独立订阅编辑页面可复制、粘贴并保存下载。PIN（PBKDF2-SHA256 + 错误锁定）、观看限制、历史/收藏管理均保留。短暂切到后台的家长解锁状态有 3 分钟限时保留；超过期限、进程被系统杀掉或明确锁定仍需重新输入 PIN。
- **极简应用网络：** 家长网络主页仅保留「连接应用网络」「添加/更换订阅」两项常用操作，首次添加有效订阅后自动进入系统授权与连接流程；更新、节点选择、文件导入及诊断收纳在「更多设置与故障排查」。支持 HTTPS Clash/Mihomo YAML 和配置备份，更新配置在下次连接时生效
- **节点管理：** Mihomo Selector 代理组选节点、当前节点延迟测试、流量查询；节点选择可在下次启动时恢复
- **仅本应用 VPN：** 无需 Root；Android `VpnService.Builder.addAllowedApplication(packageName)` 明确只让 KidsTubeTV（含预览包名）走内置 VPN，其他应用继续使用系统网络；首次需授权。Android 同时通常只能启用一个 VPN，开启本应用仍可能中断其他 VPN。打开应用后可自动启用、退出后停止；网络持续异常自动退避重连（最多 3 次），家长手动暂停不触发自动重连
- **Google 登录（可选）：** 官方 TV OAuth 设备码授权，通过电视二维码在手机上登录，读取登录账户的 YouTube 订阅；**只有家长勾选的频道**才加入白名单
- **凭据保护：** API Key、OAuth 凭据/令牌、订阅地址使用 Android Keystore AES-GCM 存储；Mihomo YAML 放在私有目录但含有节点凭据，禁止上交版本库
- **CI/CD：** push/PR 自动测试 + Debug APK Artifact；推送 `v*` 标签时通过仓库 Secrets 构建使用固定 `libmihomo-android` 的签名 APK、运行 Release Lint、验证签名、生成 SHA256SUMS 并发布 GitHub Release；需先设置固定签名密钥

## 2026-11-13 订阅兼容性回归与真实网络验证

- 新增 **使用现有 `SubscriptionClient.fetch` 的模拟 HTTP 回归测试**，覆盖类似 V2Board 的 `/api/v1/client/subscribe?token=…` 路径、FlClash 请求头、401/403、DNS 错误和 Base64 返回。源码与 CI 不包含私人 token。
- 新增可选 `SubscriptionLiveTest`，只从 `KIDSTUBE_TEST_SUBSCRIPTION_URL` 环境变量读取真实 HTTPS 订阅地址；CI 未提供此变量时会跳过真实网络测试，不把跳过误当作已经测通。
- Windows 可运行 `powershell -ExecutionPolicy Bypass -File scripts/test-subscription.ps1`，交互式隐藏输入订阅地址，执行同一 Kotlin 下载器测试，不会把 token 拼进命令行。
- 手机上的 **家长设置 → 应用网络 → 更多设置与故障排查 → 测试订阅是否可用（不保存）** 可直接在设备网络上检测，只返回脱敏结果，不替换已安装 YAML。详细步骤：[订阅排查与真实联网测试](docs/SUBSCRIPTION_TROUBLESHOOTING.md)。

## 2026-11-13 PIN 后台切换与 Clash 订阅兼容性修复

- **家长 PIN：** 解决短暂 HOME / 切换应用就重新要求 PIN 的问题。临时后台有最长 3 分钟宽限；回到应用后恢复原来 2 分钟无操作过期机制。系统文件选择器 / OAuth 浏览器的授权仍受到原先更短的授权上限保护，主动锁定或进程重启不保留 PIN 状态。
- **订阅兼容：** 网络下载依次尝试 FlClash、ClashMetaForAndroid、clash.meta、clash User-Agent；只在服务器返回网页、Base64、非 YAML 格式或 403/406/415 等客户端格式拒绝时尝试下一种。401/429、网络错误、危险 YAML 不会无限重试。私有 token 不会保存到源码或日志。
- **首次下载备用入口：** 没有可用配置且下载失败时，在「应用网络」常用操作旁显示「下载失败？导入本地 YAML」，可从可信客户端导出配置再通过文件选择器导入；成功后继续按应用级 VPN 流程连接。
- **限制：** FlClash 能下载不代表 KidsTubeTV 所在设备在未启动 VPN 前能直连相同域名；服务器实际返回格式和 Android 设备联网仍需要在设备上验证。

## 2026-11-12 设置 UI 第二轮完善（TV / 手机）

- **统一子页面：** Google TV 登录、节点选择和诊断页均改为复用 `SettingsDetailPage`，手机采用紧凑标题/返回栏与可滚动卡片，电视采用最大 880 dp 的居中详情区和较大遥控器焦点目标。
- **授权安全：** Google 页面启用 `FLAG_SECURE`，OAuth 客户端配置默认折叠；设备码轮询未真正取得新授权时不再错误显示“登录成功”。仍必须提供自己的 Google Cloud TV OAuth 客户端。
- **异步稳定性：** 节点查询/切换在离开页面后不再回填过期结果，重新进入时可重新加载；诊断离开页面会释放“正在检测”状态，防止永久无法再检测。
- **订阅与后台：** 编辑页面在手机旋转时保留未保存的输入，不把敏感 URL 放进 Bundle；后台下载完成不在别的应用上弹出 VPN 授权界面。
- **验证边界：** JVM 单测、Lint 和 APK 构建以本提交 GitHub Actions 为准，Android TV 遥控器/软键盘及手机真机联网仍需要设备验收。

## 2026-11-12 设置 UI 全面重构（TV / 手机）

- **信息架构：** 新增独立的「设置概览」，应用网络、YouTube 账号、儿童内容、家长与安全分别进入详情页。修改 PIN 和清除观看历史迁至家长与安全，避免儿童白名单页混入安全操作。
- **电视遥控器：** 固定左侧五项导航、右侧可滚动的焦点卡片。DPAD 左从右侧操作区返回对应导航项；展开更多设置后重新绑定焦点；返回键先回概览，再退出设置。
- **手机触控：** 不再显示横向 TV 导航标签；改为顶栏返回和纵向概览卡片，选择模块进入详情。已配置设备从概览进入，首次无配置优先显示网络设置。
- **订阅输入：** `SubscriptionEditorActivity` 替换长表单弹窗，支持全页 URL 编辑、剪贴板复制/粘贴和底部保存下载；使用 Android `FLAG_SECURE` 禁止将含 token 的编辑内容录入普通系统截屏；仅返回“提供商是否变化”标志，不通过 Intent 传播完整 URL。
- **业务不变：** 与原有 `SettingsStore`、`ParentSession`、下载版本门控、Android 仅本应用 VPN、Google OAuth 页面和儿童白名单复用。具体布局与验收见 [双端设置 UI 重构设计](docs/SETTINGS_UI_REDESIGN.md)。

## 本轮可靠性修复（2026-11-11）

- **订阅更新并发保护：** 增加 `SubscriptionProfileRevision` 版本门控。更换订阅 URL、从 U 盘导入 YAML、恢复备份和成功更新都会让较旧的下载结果失效；URL 校验、配置安装、节点选择清理和更新时间写入在同一配置锁内进行。失败不会覆盖已有配置或暴露 token。
- **YouTube API 传输加固：** 请求只能访问固定的 YouTube Data API 路径，查询参数编码后发送，凭据只通过请求头传输。拒绝 HTTP 跳转，分别限制成功响应（2 MiB）和错误响应（32 KiB），限制异常信息并在联网前再次检查 VPN 状态。
- **回归测试：** 新增订阅版本失效、失败重试、API Key/OAuth 请求头、VPN 中途撤销、拒绝跳转、响应上限及错误脱敏测试；以本提交对应的 Actions CI 结果为准。
- **限制：** 上述为源码和自动化验证范围，真实 Android 9 TV 的 TUN、Google OAuth 及 IFrame 仍需要独立设备验收。

## 仅本应用网络模式与家长页面简化（2026-11-12）

- Android 隧道建立时强制添加**且只添加本应用包名**，不提供全设备 VPN 模式；新增 `AppOnlyVpnScopeTest` 覆盖正式版/预览版包名和空包名防护。
- 家长「应用网络」主页仅显示**连接应用网络**与**添加/更换订阅**；节点选择、手动更新、U 盘导入、故障诊断、暂停/恢复等放到折叠高级区。
- 保存订阅后直接下载并校验；若尚未连接且未手动暂停，下载成功可直接打开 Android 系统 VPN 授权，再启动应用网络，减少遥控器操作。
- **平台限制：** 其他系统应用不经本 VPN 路由，但 Android VPN 连接有设备级独占性，可能替换已有的其他 VPN。实际 DNS/IPv6、设备 VPN 锁定模式、真机兼容仍需验证。

## 最近完善（2026-11-06）

- **首页多频道推荐：** 合并家长批准的最多 5 个频道及独立批准的视频，轮流混排，每条内容仍经过儿童标记、可嵌入与白名单验证。
- **搜索配额优化：** 同一关键词的 YouTube 搜索 ID 最多缓存 10 分钟，最多保留 48 组结果；每次浏览仍重新查询和校验视频元数据，不缓存儿童审核结论。
- **订阅更新安全：** 手动更新、后台自动更新和本地 YAML 导入互斥，下载结果有版本门控；家长保存新订阅 URL 后会立即加密记录地址，但只有通过验证的 YAML 才会替换当前配置，失败时保留旧配置；支持恢复上一份 YAML。
- **诊断：** 家长中心 → 网络代理 → 高级设置 → 设备与网络诊断，显示 Android API、CPU ABI、WebView 和 VPN 状态，可复制不含订阅令牌的报告。
- **播放恢复：** 后台切换时中断的视频审核在返回后重新请求，防止黑屏；仍需真实 Android TV 端到端测试。
- **CI：** 增加 Android Lint、单元测试、Debug APK 构建、原生 ABI 清单检查，并上传 HTML Lint 报告。

详细排查方法见 [电视调试指南](docs/DEBUGGING_TV.md)。**此项目仍以 Android TV 侧载为目标，targetSdk=32 不符合 Google Play 发布要求**；不能将 CI 成功理解为平台合规或硬件验收。

## 新用户安装

在 [Actions](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml) 中打开最新成功的 `Android TV build`，下载 **KidsTubeTV-debug-apk** Artifact 并解压安装；或从代码本地构建。

1. 在电视运行 KidsTubeTV，进入家长设置并设置 PIN。
2. 添加 YouTube Data API Key **或**后续使用 Google OAuth 授权。家长可直接粘贴 YouTube 频道地址、`@handle` 或视频分享链接，查询后确认加入白名单，无须手动查找频道 ID。
3. 初次设置进入「应用网络」，点击「添加/更换订阅」，在**独立编辑页面**输入 **HTTPS Clash/Mihomo YAML 订阅 URL** 后选择「保存并下载订阅」；更多网络设置可以从 U 盘导入完整 `config.yaml`。已配置后再次进入家长中心时先看到五分区概览。
4. 首次订阅下载成功且家长会话仍有效时会自动申请 Android VPN 授权并连接（也可返回「应用网络」手动连接）。
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
- [Clash 订阅更新与错误排查](docs/SUBSCRIPTION_TROUBLESHOOTING.md)
- [Android 9 电视验收清单](docs/TV_TEST_PLAN.md)
- [遥控器与家长网络 UI 设计](docs/TV_UI_REVIEW.md)
- [设置 UI 双端重构：页面、焦点与验收](docs/SETTINGS_UI_REDESIGN.md)

## 许可与来源

本仓库原创部分采用 GPL-3.0-only。**并未复制 BabyTube 无开放许可证的源码**；参考过它的公开需求设计。Mihomo Android AAR 采用 GPL-3.0，分发时应满足 GPL 和其他第三方依赖许可。详情参阅 [LICENSE](LICENSE)。公开仓库里不要保存代理链接、API Key、OAuth 令牌、Google Client Secret 或签名密钥。

## 功能闭环台账

- [当前实现、未完成任务与静态验收](docs/FEATURE_STATUS.md)

**代理内核：** Debug、预览版与正式 Release 使用同一份经过 SHA-256 校验的 `libmihomo-android v0.3.13` AAR。详见[功能台账](docs/FEATURE_STATUS.md)与[CI/CD](docs/CI_CD.md)。

## 用手机辅助配置（不暴露订阅密钥）

- Clash 订阅：在手机复制 HTTPS 订阅链接，使用电视支持的手机遥控器/输入法将文本粘贴到 TV；家长设置中也可一键使用电视剪贴板的链接，界面只显示域名。若电视不支持跨设备剪贴板，可通过系统文件选择器导入 U 盘上的 Clash YAML。
- Google 授权：家长中心打开 Google TV 登录，用手机扫码电视上的二维码并输入页面提供的设备验证码，无需在电视上输入 Google 密码。
- 为保护可能含节点密码的订阅数据，本应用**没有开启未经验证的局域网 HTTP 订阅上传服务**。



## v0.3.0-beta.2 TV 操作与网络更新（历史功能记录）

- 家长中心改成电视遥控器友好的两栏：左边固定“儿童内容 / 网络与 VPN / YouTube 账号”，右边是可滚动的大卡片操作，折叠高级功能。
- 在网络页直接连接或重连 VPN、导入 Clash 订阅、切换节点和查看网络诊断。系统 VPN 授权过程保留短时家长会话。
- 当前使用 `libmihomo-android` 的 TUN/DNS 实现，不向原生内核调用其它后端专属的 DNS 同步接口；实际节点连接、DNS 路由、YouTube 播放仍需电视验证。

预览版本使用 GitHub Actions 临时 Debug 签名，无法保证覆盖安装之前版本，注意备份应用配置。

## v0.3.0-beta.3 故障修复（预览）

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

当前仍使用 YouTube Data API + IFrame 与 `libmihomo-android` VPN 核心；本轮没有引入 SmartTube 非官方账号密钥、流解析及 ExoPlayer 服务层。预览版使用 CI 临时 Debug 签名，真实电视上的 VPN/Google 授权/视频播放需独立验证。

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
