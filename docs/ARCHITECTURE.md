# KidsTubeTV · 架构与安全设计（MVP）

## 设计目标

单机 Android TV 客户端，在进入儿童内容界面前建立用户授权的、仅限自身包名的 VPN。仅展示通过 YouTube Data API 严格确认的视频。允许电视原系统其他应用不经此 VPN 使用主路由网络。

## 模块分层

```text
KidsApp (all-Activity background tracking, 6s grace)
  MainActivity -- permission prompt --> KidsVpnService
       |                                   |
       v                                   v
  YouTubeRepository                  Mihomo AAR 0.3.13
       |                            (JNI + libclash.so)
       v                                   |
  AdmissionPolicy                    Android VpnService
       |                         addAllowedApplication(packageName)
       v                                   |
  PlayerActivity / YouTube IFrame          +-- protect(core outbound FD)
```

## 运行序列

1. MainActivity 确认本地 `SettingsStore.ready`（Key+白名单+YAML+PIN）。
2. 系统 `VpnService.prepare` 向成年人首次请求 VPN 授权。
3. 前台 `KidsVpnService` 加载已校验的 Mihomo AAR，调用 `quickSetup` 读私有目录中的 YAML。
4. 成功后 `Builder.establish()` 建立 IPv4 TUN，只给 `dev.kidstubetv` 走该 VPN。
5. 把 TUN FD 交给 `Clash.startTUN`。内核 outbound 调用 `protect(fd)` 避免回环。
6. UI 收到 `READY` 再允许 YouTube 网络搜索。
7. `search.list(part=snippet)` 检索视频 ID，`videos.list(part=snippet,status)` 获取真实频道 ID、`madeForKids`、`embeddable`。
8. `AdmissionPolicy` 要求儿童 + 可嵌入 + (允许频道或允许视频) 且不在禁止视频列表。缺数据默认拒绝。
9. PlayerActivity 每次播放前调用 `approveSingle` 再次向 API 复核；下一集也复核。
10. 显式退出即时断开；所有 Activity 停止 6 秒后断开，系统收回 VPN 调用 `onRevoke` 断开。

## 网络安全和工程边界

- VpnService 具有系统级范围，但 addAllowedApplication 只选择自己的 App UID/包名。不要把该服务误认为无系统 VPN 授权的普通 HTTP 代理。
- 当前只添加 IPv4 默认路由，没有 IPv6 完整性保证。在雷鸟 Android 9 上必须验证所有 Google/YouTube/WebView 请求确实通过 VPN，不允许回退直连。
- `ProxyStatus.READY` 表示 TUN 启动，不等于 Google API 可访问或节点质量已验证。UI 的搜索请求会暴露真正连接错误。
- 通过一次 `protect(fd)` 回调给 Mihomo 内核保护下行连接；仍需实测 Go DNS 和其他出口。
- 当前 YAML 从用户选择的文件导入，原样保存；需要后续提供结构校验及配置测试。绝不把代理凭据提交版本库。
- 前台 VPN 服务使用 `START_NOT_STICKY`，不随系统启动重启；明确退出停服务。系统杀进程不保证生命周期回调。
- 使用 Android platform Views，避免直接引入 BabyTube 未授权的 Compose 源码。可逐步迁移至 Compose for TV，但应保持领域和服务模块独立。

## 功能状态

| 范围 | 代码 | 验证 |
|---|---|---|
| Android TV 原创 UI | 已实现 MVP | 真机待测 |
| API v3 搜索 / 二次校验 | 已实现 | 模拟 API 单元待补 |
| 白名单、禁止规则 | 已实现 | 具备基础单元测试 |
| 家长 PIN | 已实现 | 防暴力猜测待补 |
| 本地 YAML 导入 | 已实现 | 非法格式健壮性待补 |
| App-only VPN | 已实现核心调用 | 真机 DNS / IPv6 / FD 验证待测 |
| IFrame 播放 | 已实现 | 真机 WebView 待测 |
| 自动启停 | 已实现显式退出 / 6 秒后台策略 | 真机回归待测 |

## 许可

原创工程 GPL-3.0-only。libmihomo-android 及其依赖遵循各自许可证。BabyTube 未提供开放许可，未复制其源码。


## 近期实现：VPN 生命周期、续播、家长安全

- `VpnSessionGate` 由一个递增会话序号确认是否允许异步 Core setup 回调建立 TUN；晚到的回调只被丢弃。**核心内部的异步初始化仍需将来在 FlClash 适配时处理**。
- `KidsVpnService.stop(context)` 使用 `stopService()`，避免从后台启动新的 Service 只为停止 VPN。实际异常退出与路由隔离仍需最终设备验证。
- `NativeTunCallbacks` 是可替换接口；老版 JNI 的 `protectSocket(Boolean)` 由 Service 检查，失败会结束 VPN，但旧版桥接仍无法把 Boolean 原样返回 Go。
- `WatchProgressPolicy` 校验续播点，`WatchHistory` 限制数据条目并维护本地秒数，`PlayerActivity` 按间隔保存并在重新打开最近视频时续播。
- `ParentUnlockTicket` 使用 `SystemClock.elapsedRealtime()` 而不是可手调的系统时间；连续 120 秒未交互后失效；`KidsApp` 所有活动后台化时立即调用 `ParentSession.lock()`。
