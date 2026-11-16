#!/usr/bin/env python3
"""Ensure primary UI controls remain wired to nonempty handlers.

Source-level guard only: Kotlin compile and JVM tests cover syntax and
business policy; real D-pad/touch rendering needs device instrumentation.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/dev/kidstubetv"

def source(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

def hooked(text, label, target, radius=420):
    needle = '"' + label + '"'
    begin = text.find(needle)
    if begin < 0:
        raise AssertionError(f"missing UI label: {label}")
    if target not in text[begin:begin + radius]:
        raise AssertionError(f"unwired UI control: {label} -> {target}")

settings = source("ui/SettingsActivity.kt")
for label, target in [
    ("添加在线订阅", "editSubscription()"),
    ("导入本地 YAML", "importYaml()"),
    ("重新下载订阅", "downloadSubscription("),
    ("检测订阅是否可用（不保存）", "testSubscriptionWithoutSaving()"),
    ("订阅自动更新周期", "chooseRefreshInterval()"),
    ("连接应用网络", "connectOrTestVpn()"),
    ("节点测速与选择", "ProxyActivity::class.java"),
    ("一键网络诊断", "DiagnosticsActivity::class.java"),
    ("每日观看时长", "pickDailyLimit()"),
    ("允许搜索", "store.searchAllowed ="),
    ("自动播放下一集", "store.autoplayNext ="),
    ("＋ 添加 YouTube 频道", "addChannelFromLink()"),
    ("＋ 添加单个视频", "addVideoFromLink()"),
    ("管理已批准视频", "manageApprovedVideos()"),
    ("管理已批准频道", "manageApprovedChannels()"),
    ("修改家长 PIN", "setPinDialog("),
    ("清除历史与收藏", "WatchHistory(this).clear()"),
    ("Google 授权 / 导入订阅频道", "GoogleAccountActivity::class.java"),
    ("清除 API Key / 改用 Google 授权", 'store.apiKey = ""'),
]:
    hooked(settings, label, target, radius=800 if label.startswith("清除") else 350)

main = source("ui/MainActivity.kt")
for label, target in [
    ("⌂  首页", "loadDefault("),
    ("↻  刷新当前内容", "refreshCurrentSection"),
    ("♫  英语儿歌", "YouTubeRepository(settings).search"),
    ("✦  动物世界", "YouTubeRepository(settings).search"),
    ("☾  睡前故事", "YouTubeRepository(settings).search"),
    ("▦  我的频道", "showApprovedChannelPicker()"),
    ("★  我的收藏", "WatchHistory(this).favorites()"),
    ("◷  最近观看", "WatchHistory(this).recentIds()"),
    ("⚙  家长设置", "parentGate()"),
]:
    hooked(main, label, target)

player = source("ui/PlayerActivity.kt")
for label, target in [
    ('"◀ 上一集"', "loadAt(position - 1)"),
    ('"下一集 ▶"', "loadAt(position + 1)"),
    ("☆ 收藏", "toggleFavorite()"),
]:
    clean = label.strip('"') if label.startswith('"') else label
    hooked(player, clean, target, radius=200)
for required in [
    "canControlCurrentFrame()", "iframeVideoGate.isCurrent()",
    "YouTubeRepository(settings).approveSingle", "clock.addSecond()",
    "history.savePosition", "scheduleNext()", "onVideoId("
]:
    assert required in player, "Playback or screen-time safeguard missing: " + required

repo = source("data/YouTubeRepository.kt")
filtering = source("data/YouTubeVideoScreeningPolicy.kt")
for required in ["YouTubeVideoScreeningPolicy.accept(", "approveSingle(", "search(", "uploads(", "homeFeed(", "lookup("]:
    assert required in repo, "Catalog screening entry point missing: " + required
for required in ["AdmissionPolicy.allows(", 'optBoolean("madeForKids", false)', 'optBoolean("embeddable", false)']:
    assert required in filtering, "Child screening gate missing: " + required

print("UI action wiring and video admission checks: PASS")
