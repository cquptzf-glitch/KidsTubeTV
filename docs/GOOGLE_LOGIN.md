# Google / YouTube 账户登录（可选）

## 与 API Key 的区别

- **Google 账户就是 YouTube 账户。** KidsTubeTV 可使用 YouTube Data API Key（无需登录），也可以在完成 Google OAuth 授权后直接使用只读 OAuth access token 请求公开元数据。
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


## 手机扫码简化授权（2026-11-07）

家长进入 Google 账号授权界面，电视显示 Google 官方设备验证 URL 的 QR 码及短验证码。用手机扫码打开 URL，**在手机上输入电视验证码**，再授权 YouTube 订阅的只读访问。二维码不包含 Google 访问令牌、刷新令牌、Clash 订阅密码或家长 PIN。

取消授权或设备码过期后，后台返回的 OAuth 令牌不会写入本机。登录仍需用户自行提供 Google Cloud TV/limited-input OAuth 客户端凭据，尚不提供应用作者托管的 OAuth 服务。

## beta.3 Google 登录故障分类

KidsTubeTV 不包含作者托管的共享 Google OAuth 客户端，不会直接读取系统 YouTube App 的登录信息。首次使用仍需自行创建 Google Cloud **TVs and Limited Input devices** 客户端，并启用 **YouTube Data API v3**。

新版本默认突出“手机扫码授权”，客户端设置按需展开。已保存的 Client Secret 不再预填到电视编辑框：编辑框留空即保留旧密钥。

当 Google 的 `generate_204` 检测地址无法访问但 Android VPN 已建立时，允许向真实 OAuth / YouTube API 尝试请求，并根据响应中的安全错误码区分无效客户端、过期设备码、拒绝授权、刷新令牌失效与 YouTube API 配额问题。**没有 VPN 时不进行 Google 直连回退**。

家长中心的网络诊断提供两个不带账号凭据的 HTTPS 可达性检查；这不能代替需要真实 Google OAuth 客户端和手机授权的端到端测试。
