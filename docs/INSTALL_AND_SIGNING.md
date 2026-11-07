# Android 安装失败与 APK 签名排查（beta.9）

## 先说明结论

beta.8 等早期 GitHub Actions Pre-release 使用 `assembleDebug`：**并非未签名**。Android Gradle 自动为 Debug APK 签名，但每次云端构建时的临时 debug keystore 可能不同，Android 会因签名冲突拒绝覆盖安装旧版。常见提示是“应用未安装”，ADB 错误是 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

beta.9 在 GitHub Actions 新增 **apksigner v2 签名校验**、Android 9 最低系统检查、ARMv7/ARM64 二进制检查及 ZIP 对齐检查。不符合要求则不会发布。Release 附带 `APK_INSTALL_INFO.txt`（包名、签名模式、证书指纹）与 `APK_SIGNATURE.txt`。

## 推荐：不卸载旧版，直接安装 beta.9 测试版

打开 [beta.9 Release](https://github.com/cquptzf-glitch/KidsTubeTV/releases/tag/v0.3.0-beta.9)，下载 **KidsTubeTV-v0.3.0-beta.9-FlClash-INSTALLABLE.apk**，不是 Source code.zip，也不是 lint 报告。安装步骤：

1. 查看手机/电视 Android 版本是否至少 **Android 9（API 28）**；CPU 需要支持 `armeabi-v7a` 或 `arm64-v8a`。常见的 Android x86 模拟器不包含匹配的原生 FlClash 库。
2. 在 Android 的下载管理/文件管理器中打开 APK；允许该来源临时“安装未知应用”。TV 通常需要文件管理器或 ADB 安装。只使用仓库正式 Release 的 APK。
3. **不用先卸载原 KidsTubeTV。** 未设置私有预览签名密钥时，beta.9 会安装为独立的 `dev.kidstubetv.preview.b9`，不与旧包 `dev.kidstubetv` 比较签名。桌面图标为 **KidsTubeTV 测试版**。
4. 测试版与旧版拥有不同存储，**不会自动继承旧应用的 PIN、Clash 订阅、Google 授权、批准频道**；需要自己在新测试版重新填写。两份程序不能同时运行 VPN，先在旧应用中停止 VPN。
5. 如果 GitHub 仓库已经配置专门的 *稳定预览签名 Secrets*，构建则会安装为 `dev.kidstubetv.preview`，可以用相同私钥签名的后续版本升级。以 Release 的 `APK_INSTALL_INFO.txt` 为最终依据。

> 本版“独立安装”解决了**旧签名冲突**，并不证明所有手机或电视都能安装。若设备版本不足、CPU ABI 不匹配、安装源被系统限制或 APK 下载损坏，仍会拒绝安装。

## ADB 明确查原因（手机和 TV 都可用）

```bash
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell pm list packages | grep kidstubetv
adb install -r KidsTubeTV-v0.3.0-beta.9-FlClash-INSTALLABLE.apk
```

安装成功后，通常可通过图标打开；测试版的实际包名从 Release `APK_INSTALL_INFO.txt` 获取：

```bash
adb shell am start -n dev.kidstubetv.preview.b9/dev.kidstubetv.ui.MainActivity
```

如果包名是 `dev.kidstubetv.preview`，需要替换第一段。注意：应用 Kotlin 类仍在 `dev.kidstubetv.ui` 命名空间中。

| ADB 错误 / 系统提示 | 含义与处理 |
| --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | **相同包名但签名不同。** beta.9 默认独立包名可绕过此前 beta.8 的冲突。若升级未来固定包名版本，必须用同一私钥签名。 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 包名相同但 versionCode 更低。使用新的版本或重新发布递增 versionCode。 |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | APK 缺失/损坏签名。对比 Release 附带 SHA256 和 `APK_SIGNATURE.txt`；不要安装重新打包的 ZIP。 |
| `INSTALL_FAILED_OLDER_SDK` | Android 低于 9（API 28），当前不兼容。 |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | CPU 架构不在 ARMv7 / ARM64 支持范围中。 |
| `INSTALL_FAILED_USER_RESTRICTED` / 用户禁止安装 | 在 Android 安装权限中允许当前文件管理器/浏览器安装未知应用；检查工作设备管理限制。 |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | 设备空间不足，释放空间。 |
| 点击安装没反应 | 校验下载文件是否完整，再用 ADB 获取明确错误，不要反复清除数据。 |

**不要在未备份配置时执行** `adb uninstall dev.kidstubetv`，它会删除旧应用的私有数据，Android Keystore 中的凭据一般也无法简单迁移。beta.9 的独立包名不要求这一步。

## 长期升级：使用私有、固定的预览签名证书

beta.9 构建脚本有两种模式：

- 仓库 Secrets 缺失：**Android Debug 自动签名 + .preview.b9 独立测试包名**，满足本次首次安装，但不能替代持续升级机制；下一次 beta 如果仍用临时密钥，需要新的独立测试包名。
- 配置了全部四个私有 Secrets：**固定的 .preview 包名 + 专用稳定签名证书**，之后使用相同证书、同一包名与递增 versionCode 的 APK 才可原位升级。

在本地生成一个*专门用于测试版*的密钥，备份到安全位置，不要与生产签名混用：

```bash
keytool -genkeypair -v -keystore kidstubetv-preview.jks -alias kidspreview \
  -keyalg RSA -keysize 3072 -validity 10000
```

在 GitHub 仓库 **Settings → Secrets and variables → Actions → Repository secrets** 配置：

- `KIDSTUBE_PREVIEW_KEYSTORE_BASE64`：上述 JKS 的 Base64（Linux `base64 -w0 kidstubetv-preview.jks`）
- `KIDSTUBE_PREVIEW_STORE_PASSWORD`：store password
- `KIDSTUBE_PREVIEW_KEY_ALIAS`：如 `kidspreview`
- `KIDSTUBE_PREVIEW_KEY_PASSWORD`：别名密钥密码

CI 将私钥解码到临时 Runner 文件，不提交仓库，构建完丢弃。**四项 Secrets 必须同时配置**；只有一部分时发布会失败，不会偷偷退回不一致的签名。这与正式版的 `KIDSTUBE_KEYSTORE_BASE64` 等生产 Secrets 是不同的一套密钥。

**切换规则：** 已安装 `dev.kidstubetv.preview.b9` 的用户，在第一次安装固定签名包 `dev.kidstubetv.preview` 时会是新安装；之后才可从相同稳定证书签名版本原位升级。

## 验证范围与安全

CI 只能确认 APK 的签名、格式、ABI、最低 SDK 与编译；**不等于真实 Android 手机/TV 已安装成功**。非官方文件管理器、固件限制、下载错误以及 VPN 授权仍需设备端验证。

不要把 JKS、密码、Clash 订阅或 OAuth Secret 贴进公开仓库或聊天；也不要为了能覆盖更新而公开测试或生产签名私钥。
