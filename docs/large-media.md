# 6GB 视频：同批可靠性修复

## 范围和单位

单个视频最大 **6,000,000,000 bytes（十进制 6GB）**，不是 6GiB。照片沿用 200MiB。媒体元数据、实际导入、下载计划、视频编辑输出/入库、备份和保护恢复共用此上限，不再残留 1GiB 阻断。媒体库没有新增 6GB 总量限制。旧版的 1GiB 读取限制仍存在于旧安装包中，含更大视频的备份须由本候选或后续兼容版本恢复，不能保证回退旧版后读取。

完整 ZIP 和自动保护快照沿用原有展开总额 20GiB、100001 条目及 32MiB 清单安全界限；这不是媒体库容量。超额不会截断导出。打开手动备份目标选择器前先检查容量，实际写入前再次检查；界面确认明确单份备份的限制。

## 数据路径

- 导入仍保存原件，不转码/降低画质。每次只持有 64KiB 缓冲区，计数与大小元数据使用 Long。SAF SIZE 可能为空或过时：已知大小仅用于保守预检，真实读取仍独立计数；未知大小读到 EOF，不用 readBytes/整文件数组。
- 本机暂存/恢复目标先检查可用空间，每约 8MiB 再检查，保留 32MiB 余量。不能获得可靠正数空间或空间不足即停止。此检查不能保证其他 App 不同时占用空间；底层 I/O 失败同样保持原件不变。
- 取消与流错误清理本任务 pending 文件；原用户文件只读。进程被系统强杀时不承诺自动续传，未完成 pending 可能需重新导入/正常清理；不会把它标记为成功原件。外部保护只删除本任务刚创建且写入/校验失败的文件，不清旧版本。
- 备份继续平台 java.util.zip 流式 ZIP64，不引入整套压缩库。已压缩的媒体条目采用 DEFLATED level 0，避免再次压缩 GB 视频的 CPU 开销；不改变原件字节，空间预算按未压缩原件保守计算。原件摘要在打包同一遍读取中计算校验，避免额外完整扫描一次大文件。恢复独立约束实际解压字节，而非信任 ZIP header 或清单；保留路径白名单、重复/目录拒绝、总量/单项上限、完整文件集合和 SHA-256 校验。成功前不切换资料库，失败仅移除本次 staging。
- 大文件 SHA-256 检查也可取消。视频编辑空间预估用有界乘法，避免 Long 溢出，导出轮询继续检查 6GB 上限与空闲空间。仍使用 Media3 Transformer，不新增后台平台、不宣称裁画面/遮挡免转码。

## 播放与成熟实现

继续 Media3 ExoPlayer 1.11.1 本地文件 URI 数据源、Long 毫秒 seek、原有返回位置保存、后台暂停和失败重试/返回预览。6GB 是收录政策，不保证任意编码/分辨率/设备可播放；解码仍取决于 Android 平台支持。没有为放宽文件大小升级播放器或复制其他播放器核心。

官方依据：
- [SAF 文件查询与未知 SIZE](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android ZIP64 实现](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/main/android-35/java/util/zip/ZipOutputStream.java)
- [ExoPlayer 支持的封装与平台解码能力](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [Media3 GitHub 变更说明](https://github.com/androidx/media/blob/release/RELEASENOTES.md)
- [官方 Transformer 导出与取消](https://developer.android.com/media/media3/transformer/getting-started)

## 验证分层，不能混用结论

- 固定缓冲生成流：精确 6GB 成功、6GB+1 失败、未知长度、2GiB/4GiB Long 边界、空间下降/不可知、取消及长度伪报。
- ZIP64：生成 **4GiB+1 byte** 的可压缩数据，真正经过 ZipOutputStream / ZipInputStream 往返并检查 64 位大小；不是视频文件，不证明 codec。
- Store：合成元数据与 **1GiB+65537 bytes** 稀疏来源实际流式写入、格式6备份/恢复/撤销和 SHA-256 原件核对；另测已知 6GB+1 早拒及中断清理。元数据 shadow 不是视频解码。
- 仍需手机：实际 6GB 以内录屏导入、播放/长时间 seek/返回续播、后台/锁屏、系统 SAF 未知长度、FAT32 等目标卷限制、低存储、真实 Transformer 编码和覆盖安装。没有这些结果，不称“6GB 视频实机验收完成”。
