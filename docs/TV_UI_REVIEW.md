# KidsTubeTV 遥控器与代理 UI 简化设计

更新：2026-11-12。项目选用 [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android) v0.3.13 单一原生后端。

## 儿童端

- 首页侧栏按需展开，闲置自动收缩；电视使用大封面与清晰的方向键焦点提示。
- 播放、收藏、返回和上下集使用遥控器易操作的大焦点目标；儿童不能看到订阅 URL、节点配置或密钥。
- 手机支持竖屏和横屏适配，不强制 TV 横屏布局。

## 家长设置双端框架

- 电视：左侧固定「概览 / 应用网络 / YouTube 账号 / 儿童内容 / 家长与安全」导航，右侧为大焦点卡片；方向左从操作区返回左侧导航，返回先到概览。
- 手机：取消横向 TV 标签，顶栏返回 + 单列概览/详情；无需左右方向键，操作卡片可直接点击。
- 订阅编辑使用独立 `SubscriptionEditorActivity`：电视优先聚焦返回按钮避免自动弹出软键盘；手机保留底部「保存并下载」主操作。
- PIN、观看记录清理、设备诊断位于「家长与安全」；儿童白名单页专注于内容。详见[设置 UI 详细设计](SETTINGS_UI_REDESIGN.md)。

## 家长网络页

- 常用层只有两个操作：「连接应用网络」（已连接时检测）和「添加/更换订阅」。保存后自动下载；首次成功时在家长会话有效、未手动暂停的情况下继续连接与系统 VPN 授权。
- 高级层折叠收纳：重新下载订阅、导入本地 YAML、节点切换、诊断、重启、自动更新周期、恢复备份与暂停，减少默认按钮数量。
- 新订阅需先校验 YAML，失败保留原配置；新配置在下一次 VPN 启动后生效。
- 不提供未经认证的局域网订阅上传服务；敏感信息仅在家长解锁页面展示。

## 按应用代理范围（固定，不作为设置选项）

- 服务启动时仅将 `packageName` 加入 `VpnService.Builder.addAllowedApplication`；此名单绝不能留空，否则系统默认将所有应用加入 VPN。
- 其他应用不进入 KidsTubeTV 的 VPN 路由，仍可通过系统 Wi-Fi / 蜂窝网络访问；同时运行另一个 VPN 不是该功能的目标，Android 可能撤销现有 VPN 授权。
- Android 自带的一键 VPN 授权仍需要用户确认；孩子不需要操作 Clash 规则、代理端口、全局/规则模式或 DNS 配置。

## 网络状态语义

- `READY` 只代表 Android TUN 建立，不保证 Google / YouTube 的真实连通。
- 应区分 HTTPS 测试、YouTube API 凭据检查与 IFrame 播放故障。
- 不在儿童界面展示订阅 token、OAuth Secret 或私密节点内容。
- Android 9 的 TCP/DNS/IPv6、VPN 回收和覆盖安装仍需设备验收。

实现位置：`proxy/LibMihomoCore.kt`、`proxy/LibMihomoProxyControl.kt`、`proxy/KidsVpnService.kt`、`ui/SettingsActivity.kt`。
