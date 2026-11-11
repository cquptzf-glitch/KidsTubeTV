# Android APK 安装与签名排查

主线已改为 **libmihomo-android v0.3.13**。历史 beta.9 APK 是迁移之前的构建，不应用于验证新后端。可从 [主线 CI](https://github.com/cquptzf-glitch/KidsTubeTV/actions/workflows/android.yml) 下载最新 `KidsTubeTV-debug-apk`，或本地构建：

```bash
bash scripts/fetch-mihomo.sh
gradle --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 常见安装错误

| 错误 | 解释与建议 |
| --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 同包名、不同证书；先备份配置，勿直接清除数据 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 已安装版本的 versionCode 较高，需递增版本 |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | 签名缺失或损坏，使用 `apksigner verify` 和 SHA256 检查 |
| `INSTALL_FAILED_OLDER_SDK` | 最低支持 Android 9（API 28） |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | APK 不含适合设备 CPU 的 ARMv7 / ARM64 原生库 |
| `INSTALL_FAILED_USER_RESTRICTED` | 设备禁止当前来源侧载安装 |

排查命令：

```bash
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell pm list packages | grep kidstubetv
adb logcat -s AndroidRuntime chromium
```

## 预览版与正式版

GitHub Actions 的 `KidsTubeTV preview release (libmihomo-android)` 在 main 手动运行，下载 APK 和 `APK_INSTALL_INFO.txt` 核对包名、证书指纹、ABI。

- 未设置私有预览签名时使用 `.preview.b10` 等隔离包名，能与旧版并行安装，不会自动迁移 PIN、订阅、Google OAuth 或白名单。
- 配置固定私有预览签名时包名为 `dev.kidstubetv.preview`，相同证书且 versionCode 递增才能原位升级。
- 正式版使用固定生产证书和包名 `dev.kidstubetv`；不要与 Debug / Preview 的签名混用。

**安全：** `allowBackup=false`，卸载应用可能导致私有配置及加密凭据丢失；勿公开含 token 的 YAML、OAuth Secret 或签名密钥。系统通常只允许一条活动 VPN，并行包切换前应先停止旧 VPN。

更多说明：[构建与安装](SETUP.md)、[CI/CD](CI_CD.md)。签名检查不等于手机或电视真实安装和连通测试。
