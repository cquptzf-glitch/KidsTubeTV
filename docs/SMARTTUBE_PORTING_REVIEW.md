# SmartTube 源码对照与可移植性评估（KidsTubeTV）

日期：2026-11-07。参考仓库：
- [SmartTube](https://github.com/yuliskov/SmartTube)（MIT）
- [MediaServiceCore](https://github.com/yuliskov/MediaServiceCore)（MIT，SmartTube 的 Git submodule）
- KidsTubeTV（GPL-3.0，儿童限定内容和应用专属 VPN）

> 代码参考以设计与实现行为为主。本轮重新编写 Kotlin 适配逻辑，不包含复制 SmartTube 的账号凭据、私有访问 token、视频解析器或整块源码。若今后直接复制 MIT 源文件，应保留原作者版权、MIT 原文与对应第三方依赖许可证。

## 1. 登录：两条独立管线，不能只复制授权 UI

SmartTube：
- `common/.../presenters/YTSignInPresenter.java` 调用 `YouTubeServiceManager.instance().getSignInService().signInObserve()`，界面展示来自 `youtubeapi/.../service/YouTubeSignInService.java` 的 YouTube TV 激活码（`yt.be/activate` / QR）。
- `common/.../presenters/GoogleSignInPresenter.java` 调用 `GoogleSignInService.signInObserve()`，后者委托 `OAuth2AccountManager`。
- `MediaServiceCore/youtubeapi/.../googleapi/oauth2/OAuth2Api.java` 使用 Google `oauth2.googleapis.com/device/code` 与 `token` 端点，基本与 KidsTubeTV 的 `GoogleTvAuth` 相同。
- SmartTube 账号体系还含 `YouTubeAccountManager`、多账号、令牌刷新、Retrofit 全局认证头；它依赖 `MediaServiceCore` / `SharedModules`、RxJava 与其他模块。直接复制 Presenter 不能独立登录。

KidsTubeTV：
- 保留**自有** Google Cloud “TVs and Limited Input devices” OAuth client；避免复制 SmartTube 私有/共享 client 凭据或未说明的 YouTube TV 内部接口。
- 保留设备码、手机扫码、取消/过期控制、Keystore 本地令牌保存、家长 PIN、YouTube 只读权限与频道手动白名单。
- beta.3 已改进客户端配置隐藏、OAuth 错误分类、真实端点探测；**需在 TV 上使用真实自有 Google Cloud 客户端完成端到端测试**。
- 推荐中期抽象 `AccountAuthProvider`：`begin / poll / refresh / revoke`，默认官方设备码实现；切换或引入未经官方支持的服务仅能作为独立实验，不能默默覆盖用户账号授权。

## 2. 浏览与刷新：本轮已实现可直接复用的原理

SmartTube 对照：
- `BrowsePresenter.onViewResumed / refreshIfNeeded`：返回页面后按照 3 小时阈值按需更新；
- `BrowsePresenter.disposeActions`：取消上一次 Rx 订阅；
- `saveSelectedItems / restoreSelectedItems`：记录当前内容焦点；
- `updateVideoGrid / updateVideoRows`：异步加载视频分组，附带局部更新和 continuation；
- `VideoGroupObjectAdapter`：采用 `notifyItemRangeInserted/Removed/Changed`，避免每次滚动时全量刷新。

KidsTubeTV 本轮落地：
- `CatalogRefreshPolicy`：默认 30 分钟成功刷新 TTL、失败后 5 分钟冷却，使用 monotonic uptime 防时钟回拨；JVM 单元测试覆盖首轮、过期、失败退避、配置变更。
- `MainActivity`：返回首页及页面运行期间按需重验证、可取消后台请求；网络正常时原列表在请求中继续显示，提供可见更新指示、抽屉「刷新当前内容」。
- 对于当前同一列表且内容未变化的响应：不再无意义清空卡片、重建 Glide 图片；数据变化时优先恢复视频焦点与纵向滚动位置。换白名单/登录状态时作废旧视图，避免保留已撤销批准的视频。
- 这是借鉴设计思想实现的轻量原生 View 方案；没有移入 SmartTube 的 Leanback Fragment 或 RxJava 体系，因为会显著增加应用体积和框架耦合。

## 3. 卡片与焦点：渐进式复用

SmartTube `VideoCardPresenter`：
- Glide 图片缓存、按目标尺寸解码、错误图片回退；
- ViewHolder 复用与资源释放；视频进度/角标/多种封面布局；
- Leanback 焦点与列表状态维护。

KidsTubeTV：
- 继续使用当前单动作大卡片与沉浸式 Hero，新增稳定视频焦点 key、Glide 指定尺寸、缓存策略与图片失败回退；
- 不引入与儿童场景无关的弹出菜单、长按多功能按钮、直播/短视频标签；
- 大规模列表如果后续出现卡顿，再评估逐步替换自建 `GridLayout` 为 `RecyclerView/Leanback`；不要一次性移植数千文件。

## 4. 播放：不直接移植 SmartTube ExoPlayer + YouTube 解析链

SmartTube：
- `VideoLoaderController`、`PlaybackPresenter`、`ExoMediaSourceFactory`、定制 ExoPlayer；
- `MediaServiceCore` 获取媒体格式、处理 DASH/HLS/SABR、选择客户端及格式回退；这不是可独立复制的播放 Activity。
- YouTube 的非官方接口或流地址解析可能随服务端变化而失效；其成功播放不能证明适用于 YouTube Kids 或官方 API 合规。

KidsTubeTV：
- 目前保持 `androidyoutubeplayer` IFrame 方案，避免引入未经验证的流地址解析和儿童不受控自动推荐。
- beta.3 在播放器出错时增加手动重试入口；重试经由 `loadAt` **再次检查** `madeForKids`、`embeddable`、家长频道/视频白名单、VPN 和每日限时，不自动切入 SmartTube 推荐列表。
- **此改动不能证明 IFrame 一定能够播放**。尤其 Android 9 TV 的 WebView 版本、授权网络、受限嵌入视频依旧需要真机验证。
- 未来真要使用 ExoPlayer，需要经过单独论证的 `PlaybackEngine` 抽象、可靠/有权使用的播放媒体来源、完整媒体轨道安全判定、家长限制复核和单元/设备测试，不宜直接替换。

## 5. 儿童与网络必须保持的边界

1. 即使使用 SmartTube 获取目录，不能不经 YouTube Data API 官方 `madeForKids` 和 `embeddable` 检查就显示/播放儿童内容；目录缓存不等于儿童准入缓存。
2. VPN 断开后不回退到直连 Google；登录与视频请求必须由 App 专属 VPN 运行态把关。
3. 不复用他人 OAuth Client ID、Secret、Cookie、设备令牌或任何预签名密钥。
4. 外部 SmartTube 与 KidsTubeTV 各自的 VPN / 身份认证 / 播放器进程不是可相互注入的通用模块；如果改用外部播放器，必须保证家长审核与时间限额仍然强制生效。
5. MIT 允许复制和修改，但复制的来源文件必须连带版权与许可证声明；不要将项目根目录 MIT 误认为所有内嵌模块、二进制或媒体资源均属于 MIT。

## 6. 验证顺序

- Android CI Debug 编译 + Lint + JVM 测试。
- 官方 FlClash AAR 变体编译 + 真实 VPN TCP/DNS/IPv6 实机检查。
- Google TV OAuth 设备码 → 成功授权 → token refresh → 读取频道 → 家长选择导入。
- 电视遥控器焦点/返回/刷新后位置保持、网络错误不清空屏幕、不同分辨率缩放。
- 视频允许/拒绝/网络断开/播放失败重试/日限时、前后台的边界行为。

当前只以 GitHub CI 明确完成状态为准；没有真实设备结果之前，继续标为开发预览版。


## 7. 第二轮 SmartTube 参考改进（v0.3.0-beta.5）

本轮参照 SmartTube BrowsePresenter 的异步刷新、错误状态与焦点记忆，以及独立账户服务、播放器状态事件的设计理念，重新实现以下 Kotlin 模块：

- `data/HomeFeedLoadPolicy.kt`：区分已批准来源**真实为空**、部分请求失败和全部失败。如果 YouTube API、VPN 或账号整体故障，不能伪造“0 条内容”并缓存为成功；部分成功仍能安全显示已过儿童审核的结果。新增 JVM 测试。
- `ui/MainActivity.kt`：初次加载失败后遵从 `CatalogRefreshPolicy` 5 分钟失败冷却重试，不用反复清屏；错误页提供遥控器可聚焦的主动重试入口，不显示任意网络异常原文，避免意外泄漏凭据。
- `data/TvOauthClientPolicy.kt`：先校验完整 OAuth Client ID/Secret，再提交更改。Secret 留空则保留当前密钥；更改客户端会清除本地刷新令牌与 access token。旧设备码轮询与过期 refresh 请求不能覆盖新账户状态。
- `ui/IframeVideoGate.kt`：利用 `android-youtube-player` v13 提供的 `onVideoId`，将没有视频 ID 的播放/秒数事件绑定到本次已审核视频；切换视频、VPN 丢失、Activity 退后台立即失效，回前台再次进行儿童安全审核。新增 JVM 回归测试。
- `ui/SectionFocusMemory.kt`：按首页/儿歌/动物/故事等栏目记住遥控器焦点所选视频和滚动位置，最多 16 个栏目，仅保存轻量键值，不持有 Android View。家长更改内容白名单时清理焦点记录。

没有复制或启用 SmartTube 的非官方登录凭据、SABR 视频解析器、外部账号 Cookie 或绕过 YouTube 官方播放限制的接口。该版本只完成自动化构建与纯逻辑测试，不具备真实电视 VPN/OAuth/播放验收证据。
