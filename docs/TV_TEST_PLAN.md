# 雷鸟 Android 9 / KidsTubeTV 验收计划

> 本清单是待实际执行的验证项目，不是已通过测试的证明。

## A. 安装与遥控器

- [ ] 真实 `ro.build.version.sdk` 为 28 或更高，`ro.product.cpu.abilist` 覆盖 APK 包的 AAR 架构
- [ ] Android TV Launcher 显示 KidsTubeTV 横幅并能启动
- [ ] 方向键焦点、确认、返回、滚动、搜索输入等正常；无遥控器焦点丢失
- [ ] 没有配置时不会在后台访问 YouTube
- [ ] 无 API Key、无频道白名单、无代理配置均能明确提示，不崩溃

## B. Clash/Mihomo

- [ ] 成功导入合法 YAML；Base64/无效响应拒绝
- [ ] HTTPS 订阅下载；重定向、异常状态和超大文件正确处理
- [ ] 启动软件 → 系统 VPN 授权 → 仅 KidsTubeTV UID 使用 TUN
- [ ] Mihomo 读取 `files/mihomo/config.yaml`，请求真实节点正常工作
- [ ] 节点选择、切换、URLTest、流量统计的返回数据正确
- [ ] VPN 意外被撤销不会恢复直连；处理 IPv6/DNS/QUIC 网络边界
- [ ] 后台超过 6s 自动断开；明确退出立即停止；崩溃/强杀/系统回收后没有持续运行的孤儿服务
- [ ] Google/YouTube 服务仅走预期代理；其他 TV App 网络和原系统功能不受影响
- [ ] 两个 VPN 应用之间的冲突、唤醒和网络切换行为已覆盖

## C. 儿童与 YouTube

- [ ] 无白名单不展示视频
- [ ] 非儿童视频、不可嵌入视频、黑名单视频均拒绝
- [ ] 修改白名单后旧播放队列在进入下一集前重新校验
- [ ] 批准频道的内容正常显示、封面正常加载
- [ ] YouTube Data API 403 / 配额耗尽 / 密钥错误 / 网络中断能够明确提示
- [ ] IFrame 在机型 WebView 上播放、暂停、快进/后退、结束和下一集正常
- [ ] 最近观看、收藏保存正常，清除操作需要家长权限
- [ ] 达到每日观看上限暂停播放，返回页面无法绕过（可通过更改系统时间绕过需记录风险）
- [ ] 搜索开关由家长控制，搜索结果仍经过白名单检查

## D. Google 账户（可选）

- [ ] 配置有效的 Google TV OAuth Client ID/Secret
- [ ] 通过手机访问 Google 的动态 `verification_url`、输入验证码后授权
- [ ] 取消/过期/拒绝授权、慢速轮询处理正常
- [ ] YouTube `subscriptions.list(mine=true)` 返回订阅；只导入家长勾选频道
- [ ] Google 令牌续期、本机退出、离线失效、OAuth 配额错误可恢复

## E. 发布

- [ ] CI: Debug 单元测试与 APK 构建成功
- [ ] Release: 签名 Secrets 齐备，签名 APK 与 SHA256SUMS 对应
- [ ] 从 GitHub Releases 下载 APK 安装升级，签名一致且设置未丢失
- [ ] 已核对第三方依赖及 GPL 许可证合规
