# GitHub Actions CI/CD 与 Android 签名发布

## CI：main / PR / 手动

工作流：[`android.yml`](../.github/workflows/android.yml)

1. checkout
2. JDK 17、Gradle 8.13、Android SDK 36
3. 从固定版本 Release 下载 `libmihomo-android` AAR，核对 SHA-256
4. 执行 `:app:testDebugUnitTest :app:assembleDebug`
5. 上传 `KidsTubeTV-debug-apk` Artifact，用于开发阶段 APK 下载

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
