# GitHub Actions CI/CD 与 Android 签名发布

## CI：main / PR / 手动

工作流：[`android.yml`](../.github/workflows/android.yml)

1. checkout
2. JDK 17、Gradle 8.13、Android SDK 36
3. 从固定版本 Release 下载 `libmihomo-android` AAR，核对 SHA-256
4. 执行 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug`（Lint、单元测试和 APK 构建均为阻断检查）
5. 使用 Android SDK `aapt` 检查 APK minSDK/targetSDK，并列出 `lib/` 中的 32/64-bit ARM 原生库
6. 上传 `KidsTubeTV-debug-apk` 与 `KidsTubeTV-lint-report` Artifact，用于开发阶段下载和审查

**Debug APK 是调试签名，不应作为正式稳定版给其他用户长期分发。**

## CD：推送 v* Tag 自动创建正式 Release

工作流：[`release.yml`](../.github/workflows/release.yml)。发布前在 GitHub Settings → Secrets and variables → Actions 中创建四个仓库 Secrets：

- `KIDSTUBE_KEYSTORE_BASE64`：发布用 keystore 文件的 Base64
- `KIDSTUBE_STORE_PASSWORD`：keystore 密码
- `KIDSTUBE_KEY_ALIAS`：密钥别名
- `KIDSTUBE_KEY_PASSWORD`：密钥密码

**推荐在本地生成并安全备份签名密钥：**

```bash
keytool -genkeypair -v -keystore kidstubetv-release.jks \
  -alias kidsrelease -keyalg RSA -keysize 3072 -validity 10000
```

Linux 生成 base64：`base64 -w0 kidstubetv-release.jks`；Windows PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("kidstubetv-release.jks"))
```

将 Base64 字符串仅粘贴进 GitHub Actions Secret。**不要提交 keystore、密码或 base64 到仓库、Issue、聊天。**

创建 tag（例如 `v0.2.0`）并 push：

```bash
git tag v0.2.0
git push origin v0.2.0
```

CD 将执行单元测试、`:app:assembleRelease`、签名打包和 SHA256 校验文件生成，向 Releases 上传 `KidsTubeTV-v0.2.0-android9.apk` 和 `SHA256SUMS.txt`。Tag 不带签名 Secrets 将按预期失败，而非发布不可信 APK。

## 发布检查

- PR/主分支 CI 绿灯，标签对应同一个已审查提交
- 对所有第三方二进制依赖与 GPL 合规进行核对
- 发布版本号与 Android versionCode 单调递增（当前 release.yml 使用 GitHub run_number）
- 在 Android 9 目标真机执行 [验收清单](TV_TEST_PLAN.md)
- 先备份 keystore，再面向用户提供签名 APK；不能混用 Debug 与 Release 签名
- 正式发布前建议增加 UI smoke、网络真实端到端测试、依赖扫描和 APK 签名验证

CD 只负责构建和发布，不代表自动完成真实电视的全部功能验收。


## Android 9 侧载策略

目前 `minSdk=28`、`targetSdk=32`，侧载目标是旧款 Android TV，不是 Google Play 上架版。CI 仅对 `ExpiredTargetSdkVersion` 做有说明的 Lint 例外，**其他 Lint 错误仍会阻断构建**。未来上架前需要升级 targetSDK，并审查 Android 新版前台服务、通知、VPN 权限行为。

每次 GitHub Actions 使用临时 runner 生成的 Debug keystore 可能不同；**不同 CI 构建的 Debug APK 不一定能直接覆盖安装**。需长期保留数据并做稳定升级时，使用同一份自有签名密钥打包。不要把测试 Debug APK 当作生产签名版本分发。


## 官方 FlClash 签名发布版本（2026-11-07）

`release.yml` 现默认采用固定上游 FlClash v0.9.3 和 Mihomo 子模块 SHA 构建 Go AAR，使用 `-PkidsTubeOfficialCore=true` 编译 Release，执行 `lintRelease` 和 `testReleaseUnitTest`，调用 Android `apksigner verify --verbose` 检验签名。

签名来源仍是仓库 Secrets：`KIDSTUBE_KEYSTORE_BASE64`、`KIDSTUBE_STORE_PASSWORD`、`KIDSTUBE_KEY_ALIAS`、`KIDSTUBE_KEY_PASSWORD`。**不要将密钥或 PIN 提交进仓库。** 由 `v*` 标签触发；缺失签名 Secrets 时直接失败，不发布未签名 APK。

正式 Release 附上官方核心来源版本、FlClash LICENSE 和 APK SHA256 校验文件。默认 Debug 与 Release 可使用不同内核后端，但必须共用自有稳定 Android 签名才能覆盖升级；Actions 临时签名的 Debug APK 不保证能升级至 Release。


## 预览版发布（无需用户的生产签名密钥）

推送到 `publish-preview/v0.3.0-beta.1` 分支会触发 `preview-release.yml`。该流水线从固定的 FlClash v0.9.3 源码构建官方 AAR，通过 Lint、JVM 单测及 APK 编译后，创建 `v0.3.0-beta.1` GitHub **Pre-release**，附带 APK、SHA256、核心来源与许可证。

**预览版只使用 Actions 临时 Debug 签名**，不作为稳定版，不保证跨构建覆盖升级，也不替代正式发布流程。待仓库配置生产签名 Secrets 并完成设备端功能验证后，才通过 `v*` 标签触发上文的 `release.yml` 生产签名工作流。


## beta.9：严格签名验证与可并行安装预览版

详见 [安装失败与签名排查](INSTALL_AND_SIGNING.md)。新的 `preview-release.yml` 会在发布 APK 前执行 `apksigner verify --min-sdk-version 28 --print-certs`，同时核对签名 v2、manifest 包名、minSdk 28、两个 ARM ABI 和 ZIP 对齐，并附上 `APK_SIGNATURE.txt`、`APK_INSTALL_INFO.txt` 及 SHA256。

在没有专门的预览签名 Secrets 时，测试包使用**带版本号的独立包名**（beta.9 为 `dev.kidstubetv.preview.b9`），由 Android 默认 Debug 密钥签名；它不会覆盖旧 `dev.kidstubetv`，但也不具备后续原位升级保证。配置单独的四项 `KIDSTUBE_PREVIEW_*` GitHub Actions Secrets 后，才使用固定 `dev.kidstubetv.preview` 与私有签名进行后续升级。生产包仍由 `release.yml` 单独管理生产密钥。
