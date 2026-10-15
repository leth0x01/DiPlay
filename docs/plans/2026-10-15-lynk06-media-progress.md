# 领克原车媒体进度同步 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为原车媒体中心提供有明确时间基准的播放进度，并在持有控制权且播放时定期同步。

**Architecture:** iAP2 接收层给有效进度记录单调时钟时间，元数据更新保留该基准；OEM 输出层据此外推。现有媒体中心 worker 管理刷新任务，暂停、失权和断开终止刷新，查询回调只读快照。

**Tech Stack:** Kotlin、Android Handler、现有原车 Binder、JUnit、Robolectric。

---

### 1. 进度时间基准

- 修改 `shared/src/main/java/com/shilapi/xcertplay/media/CarPlayPlaybackStatus.kt`，记录有效进度更新时间，并提供播放位置计算；状态变化而未附位置时保留连续位置。
- 扩展 `shared/src/test/java/com/shilapi/xcertplay/media/CarPlayPlaybackStatusTest.kt`，使用可控时钟覆盖元数据补充、重复位置、seek、暂停/继续、未知位置及边界。
- 修改 `common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt`，元数据比较忽略进度时间，避免增加封面重发。

### 2. 原车进度发布

- 修改 `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaCenter.kt`，新增每秒进度任务，独立于完整歌曲/封面发布；依次检查会话、控制权、令牌和播放状态。
- 修改 `common/src/main/java/com/shilapi/xcertplay/Lynk06MediaCenterProtocol.kt`，原车查询与主动发布使用同一进度计算，保留已验证的来源和 transaction。
- 扩展 `common/src/test/java/com/shilapi/xcertplay/Lynk06MediaCenterTest.kt` 与媒体元数据回归测试，验证进度事务、失权/暂停/断开停止、旧任务与查询一致性。

### 3. 验证

先运行上述定向测试，再执行 AGENTS.md 指定的完整 Android CI 命令。记录实际结果。该轮只修改媒体进度，不增加设置项。
