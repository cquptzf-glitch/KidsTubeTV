# Google / YouTube 账户登录（可选）

## 与 API Key 的区别

- **Google 账户就是 YouTube 账户。** KidsTubeTV 的“无需登录观看”使用 YouTube Data API v3 Key 访问公开元数据。
- 可选 Google 登录用于读取 `subscriptions.list(mine=true)` 的“已订阅频道”，**不用于绕开频道白名单**。
- 即使成功登录，儿童也只能观看家长主动勾选并通过 `madeForKids`/嵌入检查的视频。
- 不同步官方 YouTube 观看历史、点赞、Kids 资料；KidsTubeTV 的收藏与历史保存在本机。

Google 官方 TV OAuth 文档：<https://developers.google.com/identity/protocols/oauth2/limited-input-device>

## 获取自己的 OAuth 客户端

1. 打开 <https://console.cloud.google.com/apis/credentials>，创建或选择 Google Cloud 项目。
2. 启用 **YouTube Data API v3**。
3. 配置 Google OAuth consent screen（按 Google 审核流程设置测试用户/生产配置）。
4. 新建 **OAuth Client → TVs and Limited Input devices**，记下 Client ID 和 Client Secret。**不要使用 Web client 类型代替。**
5. 在 TV 家长设置 → Google / YouTube 账号，填写这两个值并保存（本地 Keystore 加密）。
6. 在主页面连接 VPN 后点击“开始 Google 电视授权”。
7. 电视显示 Google 提供的 `verification_url` 和 `user_code`。使用手机/电脑访问该网址，在 Google 页面输入验证码并授权 YouTube **只读**权限。不要在 KidsTubeTV 输入 Google 密码。
8. 授权成功后，进入“读取订阅频道”，手动勾选要批准的频道。未勾选的频道不会被新加入；若需删除旧白名单请在家长设置修改。

## 安全和限制

- 使用的是 Google 官方设备码 OAuth，`https://www.googleapis.com/auth/youtube.readonly` 授权。Client ID/Secret 需要开发者自行创建，应用**不预置共享的 Google 客户端密钥**。
- 访问令牌与刷新令牌使用 Android Keystore AES-GCM 加密存储；本机“退出登录”清除本地令牌，但不等同于远端撤销。可在 Google 账号的第三方应用访问页面撤销。
- 登录请求及同步订阅需要网络访问 `oauth2.googleapis.com` 与 `www.googleapis.com`，先连接 App VPN。
- Google OAuth 生产使用可能需要验证及平台配额审核；没有真机 Google 登录实测记录前，不宣称已经通过官方审核。
- 家长设置使用 PIN 限制孩子导入订阅频道；APP 级 PIN **不能替代系统级家长控制**。
