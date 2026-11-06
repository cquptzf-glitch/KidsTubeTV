# KidsTubeTV

Android TV 儿童视频客户端（面向 Android 9/API 28），以**原创实现**构建：受控 YouTube 内容检索、遥控器友好播放、内置 Mihomo 的按应用 VPN 自动启停。

> 本项目借鉴 BabyTube 的产品目标，但不复制其尚未授权的源码。当前原型需经过 Android 真机与网络环境测试后才能给儿童独立使用。

## 目标

- Android TV / 雷鸟电视，API 28 及以上
- 家长批准频道的白名单；只有经 YouTube Data API 确认面向儿童且允许嵌入的视频可播放
- 内置 Mihomo 配置及 App-only VPN：打开连接、退出断开
- 使用系统 VPN 授权，不需 Root；不代理其他电视应用
- 不在仓库提交代理订阅、节点凭据或 YouTube API Key

## 状态

初始工程提交。请参阅后续提交中的 `docs/ARCHITECTURE.md`、`docs/SETUP.md`。

## 许可

本项目原创代码以 GPL-3.0-or-later 方式分发（采用 GPL-3.0 兼容的 Mihomo SDK 时须满足相关许可证义务）。未获得 BabyTube 源码复制/衍生授权，因此不包含其源码。