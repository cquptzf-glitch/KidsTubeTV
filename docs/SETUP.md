# 构建和雷鸟电视安装

## 环境

- Android 9 (API 28) 及以上；请使用 `adb shell getprop ro.build.version.sdk` 确认电视**实际**固件
- JDK 17、Gradle 8.13、Android SDK Platform/Build Tools 36
- `armeabi-v7a` 或 `arm64-v8a` 原生库按 `adb shell getprop ro.product.cpu.abilist` 检查
- 当前版本 `0.2.0`，包名 `dev.kidstubetv`

## 从源码生成 Debug APK

```powershell
git clone https://github.com/cquptzf-glitch/KidsTubeTV.git
cd KidsTubeTV
powershell -ExecutionPolicy Bypass -File scripts/fetch-mihomo.ps1
gradle :app:testDebugUnitTest :app:assembleDebug
```

Linux/macOS：`bash scripts/fetch-mihomo.sh && gradle :app:testDebugUnitTest :app:assembleDebug`。在 IDE 中可打开工程，预先下载被忽略的 AAR 文件到 `app/libs/`。尽量避免手动更换 AAR 版本，下载脚本固定 SHA-256 校验。

可选通过 [GitHub Actions](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml) 下载成功运行后的 **KidsTubeTV-debug-apk** Artifact。

## 安装到 TV

开启电视开发者/网络调试（支持情况随固件变化），连接局域网：

```bash
adb connect 192.168.1.50:5555
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.kidstubetv/.ui.MainActivity
adb logcat -s AndroidRuntime chromium
```

示例 IP/端口必须替换为电视实际值。电视上的其他 VPN 正在运行时，KidsTubeTV VPN 可能抢占系统 VPN；请先核实冲突。

## 配置代理和内容

1. 从家长设置创建 4–12 位数字 PIN，重复失败有临时锁定。
2. 在 Google Cloud 启用 YouTube Data API v3，生成 Key；输入至少一个 `UC...` 频道 ID 或视频 ID。
3. 添加 HTTPS Clash/Mihomo YAML 订阅，点击“保存订阅地址并立即更新”；或从 U 盘导入 YAML。仅支持 YAML，暂不支持 Base64 的原始节点订阅；第一次在线下载依赖能到达订阅服务的现有网络连接。
4. 返回首页，首次接受系统 VPN 授权，等待状态显示已连接。
5. 用遥控器选择批准频道、分类、收藏或历史；家长可开启搜索并设置每日时长。
6. 在“家长设置 → 节点选择”选择 Mihomo Selector 组，测速需要 VPN 已连接。
7. 如需从已登录的 YouTube 账户导入订阅，使用 [Google TV OAuth](GOOGLE_LOGIN.md)（可选）。

## 升级前注意

- Debug 与 Release 如果签名不同，不能原位升级：Android 不允许签名不一致的 APK 覆盖安装；正式签名在首次正式版后保持固定。
- 保存的订阅 URL 和 YouTube API Key 通过 Android Keystore 加密；清除应用数据将一并清除。
- `config.yaml` 放在应用私有 `files/mihomo/`，含节点密码/订阅配置，不要把应用数据备份上传公开仓库。
- Mihomo SDK v0.3.13 **要求**读取 `<home-dir>/config.yaml`，App 已按该契约保存文件，并传入正确 `home-dir` 和 Android 版本。自动更新写入的新 YAML 在下次 VPN 连接时生效。

## 当前验证状态

GitHub CI 编译与单元测试通过时仅表示工程可构建。雷鸟 Android 9 真机的 IFrame 可播放性、Google OAuth 真实授权、Mihomo TUN 的 DNS/IPv6 行为、后台自动停止及网络断开边界，均需按 [TV_TEST_PLAN.md](TV_TEST_PLAN.md) 验收。
