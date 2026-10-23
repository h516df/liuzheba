# 0.17 档案与沉浸浏览重构

用户要求：单份档案同时包含正文、照片、横竖视频，浏览有沉浸感，并完成云端到GitHub发布闭环。基线为正式0.16/code17；目标0.17/code18。纳入原PR31/33及已验证原签名准备PR34，不回写冻结分支。

## 实现与取舍

以档案作为统一内容单元。ArchiveContent定义正文快照与冲突条件；Store在同一SQLite事务保存档案与正文，旧独立笔记原位复用。ArchiveWorkspace承载阅读和媒体整理，ArchiveScreens承载列表与编辑，AppearanceTheme保留主题。删除重复旧详情/编辑实现。没有为了重构更换数据库、备份格式或引入第二套UI框架。

阅读详情依次为标题、正文、混合媒体、补充资料；旧字段仍可展开。底部仅档案/发现，页头进入设置。编辑正文和媒体，返回自动留草稿，显式放弃才删除草稿。网格按原顺序筛选，支持拖动排序、可访问排序按钮、封面与多选移除；移除保存后进入现有可恢复回收站。

沉浸浏览使用现有Compose、Telephoto与Media3。照片默认完整展示，已有外观偏好保留；视频始终Fit，竖片不强行铺满横屏。横屏由用户主动切换，退出恢复方向。全文阅读暂停/释放播放器，返回保留进度与媒体。设备本地持久保存有限数量的浏览位置。没有强制短视频连播、自动有声播放或仿造实时模糊材质。

导入队列按当前资料代次隔离，SQLite回执与媒体关联同事务提交。崩溃恢复重试跳过已关联项，失败项及新到项继续保留。系统未授予持久读取权限的分享URI不能保证重启后仍有效；失败时保留任务以重新选择，来源不删除。

## 成熟交互参考

- [Apple Photos使用指南](https://support.apple.com/guide/iphone/view-photos-and-videos-iph3d267610/ios)：完整媒体观看、按需控制与返回上下文。
- [Day One使用指南](https://dayoneapp.com/guides/): 文字与多媒体归属同一记录。
- [Ente Photos](https://ente.io/photos/): 私人媒体浏览与本地数据边界。
- [Android Media3 PlayerView](https://developer.android.com/media/media3/ui/playerview)：视频比例、生命周期与播放器复用。
- [Android照片选择器](https://developer.android.com/training/data-storage/shared/photopicker)：选择授权与持久读取边界。

采用交互原则，产品代码独立实现；未复制上述产品UI、商标或资源。依赖许可沿用THIRD_PARTY_NOTICES。既有Orchard参考仅借鉴规则，不引入私有源码构建依赖。

## 验收分层

1. 宿主：ArchiveContentStoreTest、IncomingReceiptTest等故障注入检查事务回滚、重开、备份代次、回执和原件摘要；Compose原生图形检查混合阅读、编辑、200%字号、明暗主题及返回。
2. 隔离API35模拟器：DeviceAcceptanceTest真实解码合成横竖视频、横屏恢复与文字阅读暂停，连同既有导入、编辑、SAF备份路径。仅.verification。
3. 正式包升级：OfficialUpgradeUiTest只在显式一次性模拟器运行，从隔离测试包经正常分享向原签名0.16创建合成资料，由系统安装器确认更新。更新前后通过真实备份界面导出，比较正文、全部条目顺序及媒体字节摘要，不读取正式私有目录、不清数据、不卸载。
4. 发布：受保护android-signing环境只对unsigned候选签名；Gradle运行时不注入签名Secret。精确main提交的检查通过才签名，升级通过才创建正式Release；逐资产核对摘要、字节数、标签源码、latest与非草稿状态。

具体运行和最终状态以HANDOFF、GitHub Actions及release回执为准。物理手机、全部编码、超大库和平台在线解析兼容不属于已通过的自动证据。
