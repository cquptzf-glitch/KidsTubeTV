# KidsTubeTV 功能级代码审查与整改记录

审查基线：v0.3.0-beta.12（`d7555aa`），本次修复候选基于该代码；记录日期：2026-11-16。

## 审查方法与结论边界

将所有可见主要操作从 Android Views 的 click/遥控器事件追溯至业务方法、数据读写和网络/播放器实现。新增 `scripts/check-ui-wiring.py` 在 CI 中确认关键标签与回调连接；JVM 测试验证儿童筛查、播放器操作门控、双栏焦点及 VPN 自动更新调度。**CI 不能代替真机**：需要在 Android 9+ 电视和手机实际验证 IFrame 是否返回播放事件、YouTube 内容是否可播、订阅 HTTPS 与 DNS、系统文件选择器、遥控器焦点以及不同 WebView 提供方的兼容性。

## 按钮/功能审查清单

| 页面 | 可见操作 / 入口 | 已追溯业务调用 | 审查状态 |
|---|---|---|---|
| 儿童首页 | 菜单/遥控器左键 | `MainActivity.openDrawer` / `onKeyDown` | 已接线，待真机 D-pad |
| 儿童首页 | 首页 / 刷新当前内容 | `loadDefault`、`refreshCurrentSection`、异步请求代次 | 已接线 |
| 儿童首页 | 英语儿歌、动物世界、睡前故事 | `YouTubeRepository.search` → `lookup` → 官方视频元数据筛查 | 已接线，有 API 搜索配额与频道白名单限制 |
| 儿童首页 | 我的频道 | `showApprovedChannelPicker` → `uploads` → `lookup` | 已接线，单次读取最近 40 个上传候选 |
| 儿童首页 | 我的收藏、最近观看 | `WatchHistory` → `YouTubeRepository.lookup` | 已接线，历史结果仍需重新审核 |
| 儿童首页 | 搜索 | 仅 `searchAllowed` 为真时出现；`searchDialog` → `search` → `lookup` | 已接线，儿童模式不会直接展示搜索候选 |
| 儿童首页 | 家长设置 | `parentGate` 验证 PIN → `SettingsActivity` | 已接线 |
| 儿童首页 | 视频主卡/普通卡 | `TvHeroCard` / `TvVideoCard` → `openVideo` | 已接线，播放器再次通过 `approveSingle` |
| 视频播放 | 初次进入 / 切下一条 / 断网恢复 | `PlayerActivity.loadAt` → `YouTubeRepository.approveSingle` | 已接线；IFrame WebView 播放待真机 |
| 视频播放 | 暂停 / 继续 / 手机触摸画面 / 遥控器确定键 | `togglePlayback` + `PlayerActionPolicy.canControl` | **本轮修复**：ID 未匹配、超时、后台、VPN 中断均禁止启动播放 |
| 视频播放 | 左/右快进 10 秒 | `dispatchKeyEvent` + `canControlCurrentFrame` | **本轮修复**：不得对未审核的 IFrame 快进 |
| 视频播放 | 上一条 / 下一条 / 媒体键 | `loadAt(position ± 1)`，边界不改变当前视频 | 已接线，**本轮改进**越界提示 |
| 视频播放 | 收藏 / 取消收藏 | `WatchHistory.toggleFavorite` | 已接线 |
| 视频播放 | 观看时长 / 到时限制 | `WatchTime.addSecond` + `dailyLimitMinutes` | 已实现；依赖 IFrame PLAYING 事件 |
| 视频播放 | 续播进度 / 自动下一条 | `savePosition`、`resumePosition`、`scheduleNext` | 已实现；自动下一条再次审核 |
| 视频播放 | 无法播放 / VPN 警告 | `onError`、`pauseOnVpnLost`、`renderVpnStatus` | 已实现恢复入口；实际 IFrame 失败类型待设备验证 |
| 设置 V2 | 左侧/顶部五分类 | `showSection`，`DenseSettingsUi.twoPanes` | **本轮修复**右栏遥控器左键跳回侧栏的问题 |
| 设置概览 | 下一步配置、网络/账号/内容/家长入口 | `SetupFlowPolicy.next`、`advanceSetup`、`showSection` | 已接线 |
| 设置概览 | 每日时长、搜索/自动播放、添加频道 | `pickDailyLimit`、`showSection`、`addChannelFromLink` | 已接线 |
| 网络设置 | 添加/更换订阅、粘贴/复制/清空/取消/保存 | 页内编辑器、`SubscriptionClient.fetchDetailed` | 已接线；私密 URL 保持受保护 |
| 网络设置 | 导入 YAML、恢复配置 | Android SAF、`SubscriptionContentNormalizer`、`ProfileValidationClient`、`ProfileFileStore` | 已接线；真机厂商文件选择器兼容性待验证 |
| 网络设置 | 重新下载、检测（不保存） | `downloadSubscription`、`testSubscriptionWithoutSaving` | **本轮修复**：检测现已调用独立 Mihomo 内核校验 |
| 网络设置 | 自动更新周期 | `chooseRefreshInterval`、`SubscriptionUpdater` | **本轮修复**：VPN 持续连接期间每 15 分钟检查是否到期 |
| 网络设置 | 连接、暂停、重启 VPN | `VpnService.prepare` / `KidsVpnService` / `VpnManualPause` | 已接线，系统授权待真机 |
| 网络设置 | 节点选择 / 测速 | `ProxyActivity`、`MihomoControl.queryGroups/select/testNode` | 已接线，实时节点状态待真机 |
| 网络设置 | 一键网络诊断 | `DiagnosticsActivity` → HTTPS / API 凭据探测 | 已接线，报告脱敏 |
| YouTube 账号 | Google 设备码授权 / 导入频道 | `GoogleAccountActivity` → `GoogleTvAuth` → `ApprovedPickerPolicy` | 已接线，需真实 Google TV OAuth 配置 |
| YouTube 账号 | 设置 API Key | `editValue` → `store.apiKey` → `YouTubeRepository` | 已接线 |
| YouTube 账号 | 清除 Key / 改用 Google | `store.apiKey = ""` → 使用已授权 OAuth | **本轮新增**：以前有 Key 时无法改回 Google 授权查询 |
| 儿童内容 | 频道链接确认、频道管理 | `resolveChannel`、`confirmChannel`、`manageApprovedChannels` | 已接线，正式查询需要网络 |
| 儿童内容 | 单视频添加、视频管理 | `inspectVideoForParent`、`confirmVideo`、`manageApprovedVideos` | 已接线，**本轮校验** API 返回 ID 与请求必须一致 |
| 儿童内容 | 搜索开关、下一条自动播放 | `store.searchAllowed` / `autoplayNext` | 已实现并参与儿童主界面/播放器行为 |
| 儿童内容 | 精确允许/禁止视频 ID | `allowedVideosText` / `blockedVideosText` | 已接线，播放器与目录黑名单优先 |
| 家长安全 | 修改 PIN、会话权限 | `setPinDialog` / `ParentSession` | 已接线，**本轮新增**触控回调权限二次判断 |
| 家长安全 | 清除观看历史与收藏 | `WatchHistory.clear`，保留清除前确认 | 已接线 |
| 家长安全 | 查看/复制脱敏诊断 | `DiagnosticsActivity.safeReport` | 已接线；不输出原始订阅令牌 |

## 视频筛查真实性与实际保障

视频首页、频道、搜索、收藏、历史等 **全部调用 `YouTubeRepository.lookup`**，该方法使用官方 `videos.list(part=snippet,status)`，逐条由 `YouTubeVideoScreeningPolicy.accept` 判断：

1. 返回有效视频 ID、频道元数据与状态字段；
2. `status.madeForKids == true`；
3. `status.embeddable == true`；
4. 视频 ID 不在家长黑名单内；
5. 视频属于家长批准的频道，或视频 ID 被单独批准。

只要缺少元数据、安全字段为假、在黑名单中或者未获批准，均不进入目录。**播放器每次 `loadAt` 都额外调用 `approveSingle`；真实 IFrame 报告的播放 ID 也必须与审核 ID 一致**；本轮进一步限制所有手动播放和快进控制。

注意：这些是 YouTube **元数据 + 家长白名单筛查**，不是视觉内容的逐帧分析；不能宣称对所有儿童年龄段都绝对适合。YouTube 对嵌入视频的地区、账号、WebView 或版权限制可能导致“审核通过但播放失败”，仍必须在实际设备确认。

## 本轮修复的问题

- 播放控制的手动继续与快进未检查已上报 IFrame ID → 改为统一 `PlayerActionPolicy`。
- 播放列表边界按钮误显示本次观看已结束 → 改为首条/末条提示。
- 电视端双栏中所有左键均直跳侧栏 → 只让左侧功能组跳导航，右组保留向左的自然焦点。
- 自动订阅更新只在 VPN 初次就绪执行 → VPN 持续 READY 时每 15 分钟重查到期条件；关闭 VPN 停止调度，不做无 VPN 后台联网。
- 订阅“不保存检测”只做格式检查 → 加入隔离 Mihomo 内核真实加载校验。
- API Key 设置后无法清除，已授权 Google 账号被长期遮蔽 → 加入清除 Key 操作与当前来源状态提示。
- 家长设置通过触摸在授权失效时可能仍进入回调 → 按钮、分类和折叠入口增加临界权限二次检查。
- Google 读取频道错误原样展示异常字符串 → 改为无令牌的安全提示。
- 视频元数据验证散布在 Repository 中 → 集中至可测筛查策略，同时父母批准单视频时要求响应 ID 精确一致。

## 未完成或仍需真机验收

| 项目 | 状态与限制 |
|---|---|
| Android TV WebView 的 YouTube IFrame 真正播放 | 代码有 `YouTubePlayerView.initialize/loadVideo`、`onVideoId`、`onError`；但 CI 无电视/Google 播放环境，不能保证正常播放 |
| 订阅服务商私有 URL、VPN DNS、节点实际连通 | beta.12 配置检查与 JVM 测试通过；仍需在目标网络中逐项实测 |
| 视频年龄适宜性 | `madeForKids` 是平台元数据，不提供内容级机器识别和家长年龄区间分级 |
| 自动订阅下载最新配置的**即时热加载** | 当前仅保存新配置，待下次 VPN 连接生效，界面应提示这一行为 |
| 真机遥控器/文件选择器/WebView 兼容 | CI 无 Android UI 仪表测试；需手机与电视实机 |
| Google OAuth | 需用户提供 Google Cloud TV OAuth 客户端配置，无法由 CI 模拟真实家长账号 |

## 推荐真机验收顺序

1. 先在设置页逐项操作、确认电视右栏焦点和手机滚动正常。
2. 添加订阅 → “检测（不保存）” → 下载 → 连接 VPN → 选择节点 → 诊断 Google HTTPS。
3. 从 FlClash 导出自包含 YAML，再验证文件选择器导入与恢复旧配置。
4. 配置 Google OAuth 或 API Key，批准一个真实儿童视频和频道，首页应显示视频卡与缩略图。
5. 视频尝试播放，检查 IFrame onReady / onVideoId / PLAYING / ERROR，下一条、播放/暂停、收藏、断点续播。
6. 将视频拉入黑名单、关闭搜索和自动播放，确认主页/搜索/下一条和播放器均立即尊重家长策略。
7. 模拟断开 VPN、暂停与恢复、观看时间耗尽，确认视频不继续播放、无未经授权直连回退。
