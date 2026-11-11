# KidsTubeTV 遥控器与代理 UI 简化设计

更新：2026-11-11。项目选用 [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android) v0.3.13 单一原生后端。

## 儿童端

- 首页侧栏按需展开，闲置自动收缩；电视使用大封面与清晰的方向键焦点提示。
- 播放、收藏、返回和上下集使用遥控器易操作的大焦点目标；儿童不能看到订阅 URL、节点配置或密钥。
- 手机支持竖屏和横屏适配，不强制 TV 横屏布局。

## 家长网络页

- 常用层：VPN 连接/停止、Clash YAML 订阅链接的查看/粘贴/更新、节点选择。
- 高级层：本地 YAML 导入、节点测速、流量、诊断、脱敏错误报告，减少频繁的按钮和输入。
- 新订阅需先校验 YAML，失败保留原配置；新配置在下一次 VPN 启动后生效。
- 不提供未经认证的局域网订阅上传服务；敏感信息仅在家长解锁页面展示。

## 网络状态语义

- `READY` 只代表 Android TUN 建立，不保证 Google / YouTube 的真实连通。
- 应区分 HTTPS 测试、YouTube API 凭据检查与 IFrame 播放故障。
- 不在儿童界面展示订阅 token、OAuth Secret 或私密节点内容。
- Android 9 的 TCP/DNS/IPv6、VPN 回收和覆盖安装仍需设备验收。

实现位置：`proxy/LibMihomoCore.kt`、`proxy/LibMihomoProxyControl.kt`、`proxy/KidsVpnService.kt`、`ui/SettingsActivity.kt`。
