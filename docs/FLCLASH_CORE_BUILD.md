# 官方 FlClash Core 原生构建实验（与当前应用后端隔离）

当前 KidsTubeTV 在正常 CI 中仍使用固定 SHA-256 的 `oviron/libmihomo-android v0.3.13`。本目录新增一条**手动触发、实验性**的源码构建流水线：

- `.github/workflows/official-flclash-core.yml`
- `scripts/build-official-flclash-core.sh`

## 构建来源与边界

- FlClash 官方 <https://github.com/chen08209/FlClash> 的发布标签 **v0.9.3**（2026-11-04），非第三方改写版本。
- FlClash Go mobile 代码 `core/mobile/mobile.go`，及官方 `core/Clash.Meta` git submodule。
- Go 1.27、Android NDK 28.2.13676358，目标 Android API 28、armv7/arm64。
- 使用与官方 `build_config.yaml` 一致的 `with_gvisor` 和 Mihomo `v1.19.33` 版本设置。
- 生成实验性的 `flclash-core-v0.9.3.aar`、SHA256SUMS、GPL LICENSE 和上游 commit 记录。
- 工作流**不会**自动将 AAR 并入 KidsTubeTV，或将未验证的 APK 作为正式版发布。

## 触发

GitHub 仓库 Actions → **Build official FlClash Go core (experimental)** → Run workflow。也可在有 Go/Android SDK/NDK 的环境运行：

```bash
bash scripts/build-official-flclash-core.sh
```

该流水线只做源码构建与产物检查，不要求电视设备或联网代理节点。首次 Go/NDK 构建可能耗时较长，且依赖上游固定 git submodule 和 Go 模块可用性。

## 接入应用之前必须完成

当前 `NativeProxyCore` 的回调仍依赖旧版 `io.github.oviron.libmihomo.TunInterface`，**不可把 AAR 直接替换就视为兼容**。后续开发需：

1. 统一自有 `NativeTunCallbacks` 接口，去除运行时业务层对旧 AAR 接口的依赖。
2. 实现官方 `Mobile.quickSetup`、`Mobile.startTun(...): Boolean`、`Mobile.stopTun`、`Mobile.invokeMethod` adapter。
3. 保留官方 Android `protect(fd): Boolean` 成功语义；不能把旧版返回 `Unit` 的 protect 当成功。
4. 让官方 SDK 作为**可选择的编译后端**，默认基线 CI 仍编译老后端直至官方后端静态检查通过。
5. 对原生 core 初始化、失败清理、回调时序、配置兼容增加离线静态和单元测试。

代码 GPL-3.0 授权要求保留对应源码、修改信息和许可。生成的 AAR 若分发，必须同步满足许可义务。

> 这仅是官方源码构建流水线，不是已通过的官方后端迁移，也不保证该实验性工作流首次执行成功。实现的状态请看 [功能状态台账](FEATURE_STATUS.md)。
