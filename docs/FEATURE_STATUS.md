# KidsTubeTV 功能闭环与静态验收台账

更新：2026-11-06。本表只统计代码实现与静态检查状态。**开发未完成前暂不安排真机验收。**

## 已有代码实现

| 模块 | 实现路径 | 静态检查 |
| --- | --- | --- |
| 沉浸式电视首页、自动隐藏侧栏、大封面 | `ui/MainActivity.kt`, `ui/TvHeroCard.kt`, `ui/TvGridMetrics.kt` | TV 网格计算单元测试 |
| 儿童播放器、自动隐藏控制条、逐视频重新审核 | `ui/PlayerActivity.kt`, `data/AdmissionPolicy.kt` | AdmissionPolicy 单元测试；其余需增加纯逻辑测试 |
| 频道链接/@handle 与分享视频链接导入 | `data/ChannelReference.kt`, `data/YouTubeVideoReference.kt`, `ui/SettingsActivity.kt` | URL 解析单元测试 |
| Google TV OAuth、父母审核后导入订阅 | `data/GoogleTvAuth.kt`, `ui/GoogleAccountActivity.kt` | 手工流程待静态模拟服务测试 |
| 白名单撤销、批准视频删除 | `data/ApprovedPickerPolicy.kt` | 选择器集合策略单元测试 |
| Mihomo 代理、配置导入/更新/备份回滚 | `proxy/KidsVpnService.kt`, `data/SettingsStore.kt` | YAML、订阅互斥与格式测试 |
| 节点切换和选择记忆 | `proxy/MihomoControl.kt`, `data/ProxySelectionPolicy.kt` | 节点选择策略与响应解析单元测试 |
| VPN 断线提示及会话隔离 | `proxy/ProxyNetworkHealth.kt`, `proxy/ProbeSessionGate.kt` | VPN 探测会话回归测试 |
| 子频道混排、搜索 ID 短时缓存 | `data/HomeFeedMixer.kt`, `data/SearchIdCache.kt` | 混排与缓存单元测试 |
| 家长高级设置与脱敏诊断 | `ui/SettingsActivity.kt`, `ui/DiagnosticsActivity.kt` | Lint 与 Kotlin 编译 |

## 未完成的实际开发工作（优先级）

### P0：代理核心与安全边界

- 当前仍是第三方 `oviron/libmihomo-android`，**尚未替换成** FlClash 官方 `chen08209/FlClash` 的 Go/gomobile 构建。必须选定上游 commit，建立可重复构建原生库的流水线，适配新的 `Core.startTun()` 返回值、`protect(fd)` 成功标志、配置载入参数与策略组管理回调。
- 对 VPN 建立/异常/强制停止和系统回收完善边界处理，避免仅因 TUN 已建立就报告 YouTube 一定可用；应用层阻止离线数据请求不能取代系统级防泄漏保证。
- 为核心初始化、自动更新与停机的异步交叉增加可注入的状态机和单元测试。已有会话序号处理旧回调，但不能等同于完整的原生网络停机保证。

### P1：父母配置与内容体验

- 设计手机辅助配置的最简方案：优先系统提供的 TV 手机键盘/分享入口；若做局域网配对，必须先实现短时一次性凭据、强身份验证、最小监听、保密订阅信息与关闭服务机制。当前**没有**对局域网开放配置 API。
- 扩展大量获批频道的分页浏览/频道名称缓存（当前首页最多混排前 5 个频道）；保持儿童菜单低复杂度。
- 改进 Google OAuth 缺少凭据时的初次配置引导、令牌更新失败恢复和异常分类；不能要求儿童在 TV 输入密码。
- 播放器中断/自动下一集/时长上限使用可模拟时钟与测试替身补足状态机测试。

### P2：发布工程

- 固定发布签名证书，并在 GitHub Secrets 完成配置，执行签名 Release 构建、依赖许可证核对与版本规则检查。
- 继续保持 `lintDebug`、`testDebugUnitTest`、`assembleDebug` 为阻断检查；新增功能都需有测试。当前面向侧载 Android 9，尚不具备上架 Google Play 的目标 API 设置。

## 本阶段完成判据

在不进行设备测试的阶段，只将**源码完整实现 + CI 无错误 + 单元测试通过 + 文档状态准确**视为开发阶段完成。不能以 APK 可编译证明真实 Google 登录、IFrame 视频播放、Mihomo VPN 或节点联网已经通过验收。软件功能全部补齐后，再安排设备级验证。
