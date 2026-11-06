# KidsTubeTV 功能闭环与静态验收台账

更新：2026-11-06。本表只统计代码实现与静态检查状态。**开发未完成前暂不安排真机验收。**

## 已有代码实现

| 模块 | 实现路径 | 静态检查 |
| --- | --- | --- |
| 沉浸式电视首页、自动隐藏侧栏、大封面 | `ui/MainActivity.kt`, `ui/TvHeroCard.kt`, `ui/TvGridMetrics.kt` | TV 网格计算单元测试 |
| 儿童播放器、自动隐藏控制条、逐视频重新审核与继续观看 | `ui/PlayerActivity.kt`, `data/AdmissionPolicy.kt`, `data/WatchProgressPolicy.kt` | 准入和续播秒数边界测试；播放状态机仍需补充离线模拟 |
| 频道链接/@handle 与分享视频链接导入 | `data/ChannelReference.kt`, `data/YouTubeVideoReference.kt`, `ui/SettingsActivity.kt` | URL 解析单元测试 |
| Google TV OAuth、父母审核后导入订阅 | `data/GoogleTvAuth.kt`, `ui/GoogleAccountActivity.kt` | 手工流程待静态模拟服务测试 |
| 白名单撤销、批准视频删除 | `data/ApprovedPickerPolicy.kt` | 选择器集合策略单元测试 |
| Mihomo 代理、配置导入/更新/备份回滚 | `proxy/KidsVpnService.kt`, `data/SettingsStore.kt` | YAML、订阅互斥与格式测试 |
| 节点切换和选择记忆 | `proxy/MihomoControl.kt`, `data/ProxySelectionPolicy.kt` | 节点选择策略与响应解析单元测试 |
| VPN 断线提示、后台停机与会话隔离 | `proxy/KidsVpnService.kt`, `proxy/VpnSessionGate.kt`, `proxy/ProbeSessionGate.kt` | 两种会话状态机及探测回归测试 |
| 子频道混排、搜索 ID 短时缓存 | `data/HomeFeedMixer.kt`, `data/SearchIdCache.kt` | 混排与缓存单元测试 |
| 家长 PIN 超时/后台锁定与脱敏诊断 | `data/ParentUnlockTicket.kt`, `KidsApp.kt`, `ui/*Activity.kt` | PIN idle 超时与后台锁定逻辑测试，Lint、编译 |

## 未完成的实际开发工作（优先级）

### P0：代理核心与安全边界

- 当前仍是第三方 `oviron/libmihomo-android`，**尚未替换成** FlClash 官方后端。已新增固定 v0.9.3 的实验性 Go/gomobile 源码构建流水线（仅手动触发，**尚未执行确认产物**），下一步要完成 `Core.startTun()` 返回值、`protect(fd)` 成功标志、配置参数和节点管理 API 的 adapter，并完成官方后端静态构建。
- 对 VPN 建立/异常/强制停止和系统回收完善边界处理，避免仅因 TUN 已建立就报告 YouTube 一定可用；应用层阻止离线数据请求不能取代系统级防泄漏保证。
- 为核心初始化、自动更新与停机的异步交叉增加可注入的状态机和单元测试。已有会话序号处理旧回调，但不能等同于完整的原生网络停机保证。

### P1：父母配置与内容体验

- 设计手机辅助配置的最简方案：优先系统提供的 TV 手机键盘/分享入口；若做局域网配对，必须先实现短时一次性凭据、强身份验证、最小监听、保密订阅信息与关闭服务机制。当前**没有**对局域网开放配置 API。
- 已增加「我的频道」折叠菜单，支持访问全部批准频道并复用本地频道名称；首页仍只混排前 5 个频道。继续评估大频道库的分页/缓存性能，保持儿童菜单低复杂度。
- 改进 Google OAuth 缺少凭据时的初次配置引导、令牌更新失败恢复和异常分类；不能要求儿童在 TV 输入密码。
- 播放器中断/自动下一集/时长上限使用可模拟时钟与测试替身补足状态机测试。

### P2：发布工程

- 固定发布签名证书，并在 GitHub Secrets 完成配置，执行签名 Release 构建、依赖许可证核对与版本规则检查。
- 继续保持 `lintDebug`、`testDebugUnitTest`、`assembleDebug` 为阻断检查；新增功能都需有测试。当前面向侧载 Android 9，尚不具备上架 Google Play 的目标 API 设置。

## 本阶段完成判据

在不进行设备测试的阶段，只将**源码完整实现 + CI 无错误 + 单元测试通过 + 文档状态准确**视为开发阶段完成。不能以 APK 可编译证明真实 Google 登录、IFrame 视频播放、Mihomo VPN 或节点联网已经通过验收。软件功能全部补齐后，再安排设备级验证。

## 2026-11-06 最近合入

- 已选代理节点恢复：`ProxySelectionPolicy`、`SettingsStore.selectedProxyNodes`、`CurrentMihomoAarCore.setup`。切换成功后持久化；更换订阅或导入本地 YAML 时清理旧选择。
- VPN 启停/探测回调隔离：`KidsVpnService.sessionGeneration`、`ProbeSessionGate` 及回归测试。
- 父母撤销与收藏控制：Google 订阅取消勾选可撤销、已批准单视频列表可删。
- 完整频道访问：隐藏菜单「我的频道」，支持全部批准频道，标题从本地缓存获取，播放仍执行准入审核。
- 应用层数据请求门控：Google/YouTube API 在 VPN 未准备好或探测已确认不可达时拒绝请求；不会声称这能替代 OS 级的 VPN 安全保证。
- FlClash 官方源码构建：`official-flclash-core.yml`、`build-official-flclash-core.sh`、[构建说明](FLCLASH_CORE_BUILD.md)。当前默认构建**仍使用旧封装**，官方实验性工作流未证明成功，不应将其记为已迁移。


## 2026-11-06 最新一轮开发

- **VPN**：后台停止操作改用 `stopService`，不再创建一个新的后台 Service 传递 STOP；新增可测试的 `VpnSessionGate` 防旧初始化回调产生新 TUN。
- **核心适配**：`NativeTunCallbacks` 变为 KidsTubeTV 自己定义的接口，`CurrentMihomoAarCore` 负责转换到旧版 `TunInterface`。旧 JNI 的保护回调仍然只返回 Unit，**尚不具备官方 FlClash 的完整 Boolean protect 语义**。
- **最近观看进度**：`WatchHistory` 本地记录最多 50 条最近视频的续播点，每 12 秒更新；播放结束和清空历史时删除相应进度。每次进入播放页仍重新审核是否允许观看。
- **家长权限**：解锁后连续无操作约 2 分钟自动到期，切出应用立即失效；家长界面按键再次校验，第一次设置 PIN 不能通过返回键跳过。
- **限制**：上述完成项是 Kotlin 功能与静态检查，不代表 Android TV 真机的 Native TUN、系统限制行为和视频 IFrame 已验收。

