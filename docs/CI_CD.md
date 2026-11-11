# KidsTubeTV GitHub Actions / APK 发布

## 统一原生依赖

`app/build.gradle.kts` **只依赖** `app/libs/libmihomo-android-v0.3.13.aar`，对应 [oviron/libmihomo-android](https://github.com/oviron/libmihomo-android)。仓库不提交 AAR 二进制；构建前用 `scripts/fetch-mihomo.sh` 或 `scripts/fetch-mihomo.ps1` 下载并校验 SHA-256：

`c6c2787e5dd888a8f9193501506dfa3a4f8a38fbd51adefccf67d84d0c8e0fb5`

本地构建：

```bash
bash scripts/fetch-mihomo.sh
gradle --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

## main CI

[android.yml](../.github/workflows/android.yml) 在 main 推送、PR、手动运行时使用 JDK 17、Gradle 8.13、Android SDK 36，下载 AAR，运行 Lint、单元测试、Debug APK 构建并上传制品。Runner 使用的临时 Debug 签名证书可能发生变化。

## 预览版：在 main 手动触发

[preview-release.yml](../.github/workflows/preview-release.yml) 由 `workflow_dispatch` 手动触发，仅允许 main。填写未使用的预览版本（默认 `0.3.0-beta.10`）、递增 versionCode 及包名后缀。下载相同 AAR，运行 Lint/单测/签名 Debug APK 构建，检查 `apksigner` v2、Android 9、ARM ABI、ZIP 对齐和 SHA-256，最后发布 Pre-release。

配置完整的四个 `KIDSTUBE_PREVIEW_*` Secrets 时使用固定私有证书和 `dev.kidstubetv.preview`；否则使用 `.preview.b10` 等版本隔离包名与临时 Debug 签名，适合新安装，**不保证覆盖升级**。

## 生产发布：v* 标签触发

[release.yml](../.github/workflows/release.yml) 使用以下私有 GitHub Actions Secrets：

- `KIDSTUBE_KEYSTORE_BASE64`：JKS 的 Base64
- `KIDSTUBE_STORE_PASSWORD`：JKS 密码
- `KIDSTUBE_KEY_ALIAS`：密钥别名
- `KIDSTUBE_KEY_PASSWORD`：别名密码

请离线备份稳定生产证书；**不得提交 JKS、私钥、密码、订阅 token 或 Google Secret。** 流水线进行 Release Lint、单测、签名 APK 构建与 v2、ABI、ZIP 检查，上传 `LIBMIHOMO-SOURCE-INFO.txt` 和 `SHA256SUMS.txt`。

目标为 Android 9+ 侧载，`minSdk=28`，`targetSdk=32` 尚不符合 Google Play 最新 targetSdk 要求。原位升级必须使用相同包名、证书及递增 versionCode。**构建成功并不代表设备上的 VPN、Google OAuth 或 YouTube IFrame 已通过验收。** 请参阅[电视验收清单](TV_TEST_PLAN.md)。
