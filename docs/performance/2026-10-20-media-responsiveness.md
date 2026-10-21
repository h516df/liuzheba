# v0.16 媒体与资料响应诊断

基线：`079b904ec54d7f40d7c04d79f969014c99ae8d85`。本批不换数据库或图片框架、不改备份格式、不压缩/移动原件、不降低校验强度。

## 实际链路与发现

- 导入：`AppModel.importPhotos → Store.importPhotos → LargeMediaIo.copy → inspectMedia → draft`。v0.16 已用 64 KiB 固定缓冲，一次读取时同步计算 SHA-256，不存在“每个视频重复哈希三遍”的导入热点。ContentProvider、存储速度与视频解析仍需真机测量。
- 自动保护：`runAutomaticProtection → protectNow → RecoveryFolder.protect`。原实现持有同一数据库 gate 贯穿全部外部副本回读，导致普通 `load/save/label` 在大视频校验时排队。每次 Activity 恢复又触发强制保护，启动也会校验。
- 草稿：200 ms 防抖后写一条草稿，随后读取全部 9 类对象、反序列化所有档案/媒体。多数完成操作又在 try 和 finally 各 reload 一次。
- 标签：创建/改名先加载整库；删除标签无论是否关联都重写所有档案和草稿。
- 缩略图：视频已经有 SHA 键磁盘缓存、2 个解码槽和 480×640 有界缩略图，不重造。`photoFile` 的路径 getter 却每次调用 `mkdirs`，包括 Compose 构建时，改为 Store 打开时创建目录、取路径时不访问文件系统。
- ZIP 备份：媒体打包时已有 SHA 校验，最终目标也完整回读。原实现另读临时 ZIP 计算摘要；现将该摘要合入写目标的那次读取，保留目标完整回读比对。

## 实施与安全边界

1. 去掉成功路径重复 reload；草稿写入成功只更新内存中相应草稿，不扫描无关档案。标签创建/改名只读标签；删除仅重写关联档案/草稿。
2. 自动保护在短锁内取一致快照，外部 I/O 离开数据库锁。独立 protection gate 固定快照原件，永久清理、恢复、撤销恢复、换保护目录和关闭数据库必须等待它。普通资料改动增加 revision，旧快照完成后如发现新 revision，绝不标记最新资料“已保护”，保持待保护并排下一次。每个外部原件仍完整回读，不使用 mtime/大小缓存跳过校验。
3. 保留既有 3 秒变更合并、conflated 队列和启动完整重验。普通前台返回只续 dirty/未完成任务；明确重试仍完整校验。任务在应用进程中运行，未承诺杀进程后继续；重开会校验/续上。没有新加 WorkManager、权限或云端上传。
4. 详情直接选媒体，不先建编辑草稿。复制和关联由同一 Store 操作完成；已有未提交草稿时，正式档案与草稿原子追加相同媒体，但不提交草稿的其他字段。SQL 失败只清理本批新副本，picker 源文件不动；取消选择不写任何草稿。提交完成、回主线程之前的取消也会先同步已提交编辑器状态，防止旧编辑器随后覆盖新媒体；已用暂停 Main 调度器回归验证。
5. 大文件导入增加节流的已复制 MiB/总量反馈。保留 6 GB/200 MiB、空间余量、流式限制、取消清理、来源只读和独立副本。

## 同机结果

| 测项 | 基线 | 修改后 |
| --- | ---: | ---: |
| 新增标签，中位数 | 41.101 ms | 1.065 ms |
| 标签改名，中位数 | 45.148 ms | 0.895 ms |
| 删除标签，单次 | 383.806 ms | 132.359 ms |
| 删除标签实际档案写入 | 1000 行 | 20 行 |
| 外部保护阻塞 750 ms 时普通读库 | 763.349 ms | 4.568 ms |
| 128 MiB ZIP 备份，中位数 | 515.169 ms | 485.139 ms |

备份样本有波动（修改后 466.7–559.7 ms），不能据此承诺稳定加速比例。可确定的是少读一遍临时 ZIP；完整目标回读仍在。标签测量反映避免无关 JSON 解析/写入，保护测量反映消除锁等待，不代表真实用户手机的绝对耗时。

## 验证方法

`MediaPerformanceBenchmarkTest` 使用 Robolectric API 28 / legacy SQLite 与 JVM 文件 I/O，预置 1000 档案、4000 媒体元数据，标签操作 9 次去掉前两次；删除标签的真实 INSERT 数量由 SQLite trigger 记录。保护测试人为阻塞 provider 750 ms，测量同时 `Store.load`。备份测试使用真实 128 MiB 文件和完整输出回读，4 次去掉预热第一次。

基线与修改后运行完全相同测试。基线工作树只添加该测试文件，不改生产源码；修复工作树运行相同用例。

```sh
./gradlew :app:testDebugUnitTest --tests dev.h516.shicang.MediaPerformanceBenchmarkTest
./gradlew :app:testDebugUnitTest \
  --tests dev.h516.shicang.MediaResponsivenessSafetyTest \
  --tests dev.h516.shicang.DirectMediaImportTest \
  --tests dev.h516.shicang.ProtectionStoreTest \
  --tests dev.h516.shicang.LargeMediaStoreTest \
  --tests dev.h516.shicang.LargeMediaIoTest \
  --tests dev.h516.shicang.EditorPersistenceTest \
  --tests dev.h516.shicang.BackupStoreTest \
  --tests dev.h516.shicang.UnifiedBackupStoreTest
```

本批原子追加覆盖系统媒体选择器的详情和编辑器路径。分享/下载收录仍为原有草稿工作流，保留未完成导入警告，未宣称对所有来源做 exactly-once 改造。

测试仅用合成资料。时长是 host 微基准，不是真手机、真实 ContentProvider、视频解码吞吐、UI 帧率或电池表现；不可宣传为手机导入加速倍数。原始结果保存在 `docs/performance/results/baseline.xml` 与 `after.xml`。

## 回归范围

52 项存储/媒体安全回归通过；修正取消后编辑器发布时序后，另重跑受影响的 9 项导入/编辑用例。覆盖：

- 1 GiB 以上未知长度媒体真实流式导入、备份、恢复与撤销；6 GB 边界元数据、取消部分文件清理和来源不改动。
- 慢外部保护期间普通读写可完成；新 revision 不误报已保护；purge、restore、undo 必须等待快照原件；排队 purge 取消无删除；保护取消释放 pin。
- 外部原件等长等时间戳损坏仍通过完整摘要发现并修复，旧版本不覆盖；撤销授权、损坏回读、失败写入/清单、部分导入警告仍明确保留。
- 详情取消 picker 不生草稿；正式＋已有草稿只追加媒体；事务失败全回滚；提交后、回 Main 前取消也不会被旧 editor 覆盖。

最终整合的 UI/全量构建由同批集成验证承担，本报告不替代其结果，也不替代真机覆盖升级验收。

## 参考及取舍

- [Android SQLite 性能最佳实践](https://developer.android.com/topic/performance/sqlite-performance-best-practices)：减少不必要读取/写入，沿用现有索引和事务，不改 durability。
- [Aves ThumbnailFetcher.kt](https://github.com/deckerst/aves/blob/develop/android/app/src/main/kotlin/deckers/thibault/aves/decoding/ThumbnailFetcher.kt)，[BSD-3-Clause](https://github.com/deckerst/aves/blob/develop/LICENSE)：按显示尺寸请求、内容签名和缩略图缓存。这里核对已有实现，未替换 Coil 或移植代码。
- [Fossify Gallery MediaAdapter.kt](https://github.com/FossifyOrg/Gallery/blob/main/app/src/main/kotlin/org/fossify/gallery/adapters/MediaAdapter.kt)，[GPL-3.0](https://github.com/FossifyOrg/Gallery/blob/main/LICENSE)：结果变化才刷新。仅借鉴设计，未复制 GPL 源码；本批直接消除上游重复读取，而不只在 UI 重复比较结果。
