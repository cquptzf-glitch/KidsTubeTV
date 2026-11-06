# 官方 FlClash Core 原生构建实验（与当前应用后端隔离）

当前 KidsTubeTV 在正常 CI 中仍使用固定 SHA-256 的 `oviron/libmihomo-android v0.3.13`。本目录新增一条**手动或官方适配文件变更时触发**的源码构建流水线：

- `.github/workflows/official-flclash-core.yml`
- `scripts/build-official-flclash-core.sh`

## 构建来源与边界

- FlClash 官方 <https://github.com/chen08209/FlClash> 的发布标签 **v0.9.3**（2026-11-04），非第三方改写版本。
- FlClash Go mobile 代码 `core/mobile/mobile.go`，及官方 `core/Clash.Meta` git submodule。
- Go 1.27、Android NDK 28.2.13676358，目标 Android API 28、armv7/arm64。
- 使用与官方 `build_config.yaml` 一致的 `with_gvisor` 和 Mihomo `v1.19.33` 版本设置。
- 生成实验性的 `flclash-core-v0.9.3.aar`、SHA256SUMS、GPL LICENSE 和上游 commit 记录。
- 工作流将 AAR 用于**独立的官方 SDK 测试 APK** 编译，但不会替换日常 CI 默认后端，也不会自动创建正式 Release。

## 触发

GitHub 仓库 Actions → **Build and statically check official FlClash core** → Run workflow。也可在有 Go/Android SDK/NDK 的环境运行：

```bash
bash scripts/build-official-flclash-core.sh
```

该流水线只做源码构建与产物检查，不要求电视设备或联网代理节点。首次 Go/NDK 构建可能耗时较长，且依赖上游固定 git submodule 和 Go 模块可用性。

## 已实现的双后端架构

- 默认：`app/src/legacy/java` 编译原 `libmihomo-android` 后端。
- 官方：`app/src/official/java` 提供 `Mobile.quickSetup/startTun/invokeMethod` 的 Kotlin 适配器。
- 使用 `-PkidsTubeOfficialCore=true` 编译官方后端，而不是同时加载两个 JNI SDK。
- `NativeTunCallbacks` 保留 Boolean socket-protect 结果，官方方法选择与错误响应由 `OfficialCoreProtocol` 校验。
- 独立 CI 负责官方 Go AAR → Lint/Unit Test → APK 流程，不触及现有默认 APK。

## 已确认与待完成

- 已确认：官方 v0.9.3 可生成 `classes.jar`、ARMv7/ARM64 `libgojni.so`（Actions #42255934137）。
- 首次官方 APK 编译在复制 AAR 时发现缺少忽略目录 `app/libs`，已添加 `mkdir -p`；后续结果以独立 Actions 为准。
- 官方 SDK APK 静态构建仍未确认通过，且没有进行真机测试、DNS/IPv6 防泄漏认证。
- 默认发布签名和开源许可证符合性需在正式 Release 前继续核对。

代码 GPL-3.0 授权要求保留对应源码、修改信息和许可。生成的 AAR 若分发，必须同步满足许可义务。

> 这仅是官方源码构建流水线，不是已通过的官方后端迁移，也不保证该实验性工作流首次执行成功。实现的状态请看 [功能状态台账](FEATURE_STATUS.md)。
