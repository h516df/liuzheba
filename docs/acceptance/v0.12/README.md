# 留册 0.12 原生验收

- 生产源码：`58171a2b143ce633689a2caf9d9c49d1ca4e2cea`。此后的提交只增加证据/说明。
- 正式包：`dev.h516.shicang`，0.12 / code13；大小、SHA256、签名证书见 `build.json`。构建完成并通过 apksigner 验证。
- 原生交互：隔离包 `dev.h516.shicang.verification`，Android 16 / API36 / x86_64，AVD WalkPaint_Google_API36_V044（emulator-5588）。全部使用合成资料与色块测试图片。
- 常规视口：1080×2400 px、420 dpi、font_scale 1.0、月白主题。
- 小屏大字：720×1600 px、320 dpi（360×800 dp）、font_scale 1.3、曜石主题；验后恢复设备原设置。

## 验证结果

36 JVM 用例通过；8 条原生路径最终通过：

| 用例 | 实际验证的结果 |
| --- | --- |
| ContentFirstUiTest | 默认档案墙不显示成长；封面进详情，媒体入口进沉浸浏览；封面左滑落盘回收并恢复，原照片/字段/分类保持 |
| DesignV19Test | 列表/封面切换、筛选、详情、编辑草稿、字段撤销、模板不复制内容、标签新建/移除/再选、保存重开、搜索结果与复制链接 |
| VideoUiTest | 视频播放、声音与生命周期恢复、长按2倍速及松手恢复、双击快进退、拖动进度 |
| PhotoSwipeTest | 来回翻页69帧，最低可见比例1.0；稳定图片组件保持，详见photo-swipe-result.txt |
| ShareSwipeTest | 列表左滑取消/确认/恢复；真实ACTION_SEND入口预填快手直播档案，保存后原文与字段落盘 |
| GrowthUiTest | 周目标持久化、徽章重复打开不重复授予、记录展开、最近任务隐藏开关 |
| BackupPrivacyTest | 导出前完整隐私确认；取消不创建文件；确认进入系统保存位置选择 |
| ViewerLockTest | EXIF显示方向、查看器返回、后台锁定后不显示私人标题/图片 |

小屏深色另外通过 ContentFirstUiTest、ShareSwipeTest（2条，原始输出 small-dark-final.txt）。截图已检查标题、控件、长档案名、滚动和主要入口；不把视觉检查表述为用户已认可。

## 失败与修复边界

首轮3项探针未通过：新增封面恢复断言误用保存前对象（Store会自动补封面ID）；旧探针对已选Tab发点击；图片探针用文本搜索API未找到Compose节点。修正为保存后的真实基线、直接等待档案页、遍历可见节点后触摸；封面和69帧图片复跑通过。

分享复跑暴露既有运行问题：已知完整直播链接仍访问网络，网络慢时卡在识别。修复为路径已能确定主页/直播/作品类型时直接本地预填，未知短链接继续现有受限重定向。新增本地解析时间边界单测，36 JVM通过；最终包的分享和小屏完整路径通过。

UI截图与其余未受影响路径先在31f2a0e运行；58171a2仅改上述分享逻辑与探针，未改变UI、播放器或数据格式，因此复用对应未变化路径证据。最终正式包哈希已从旧d52426…变为build.json中的3ca05f…，不得发布旧包。

## 正式发布与覆盖升级

[v0.12 正式发布](https://github.com/h516df/shicang/releases/tag/v0.12) 固定目标为 `501973a6299fe3cc196a86efdd9ce3140ee1d748`；合并后的 tree 与已验分支 `34b6b61f161c435e9c03df4e3578115265051279` 一致（`dec40a07d31361da2b08b4418027989ca6248e18`）。发布前 release 分支干净。后续文档提交不改变发布目标或资产。

发布回执见 [publication.json](release/publication.json)。安装包、中文说明和 SHA256 清单均已通过无身份授权的下载回读，逐项核对大小与摘要，见 [anonymous-readback.json](release/anonymous-readback.json)。正式 APK 为 3,282,399 字节，SHA256：`3ca05fc449509757914e6e5bd588a5b7794967b0e1443ec7da51a8a5d0044d24`。

在同一 API36 模拟器安装已发布的正式 0.11/code12，用真实界面建立匿名档案 `Upgrade-v012-fixture` 和 `Note: Anonymous-kept-value`，再对匿名下载的 0.12/code13 正式包执行 `adb install -r`，没有卸载或清数据。启动后档案数量为 1，打开详情确认名称、字段名与值保留；版本信息核对为 0.12/code13。证据见 [official-upgrade.json](release/official-upgrade.json)、[实际详情截图](release/upgrade-after.png) 和 [界面层级](release/upgrade-preserved.xml)。这项覆盖升级补充正式 release 包的启动与数据保留证据；上述完整交互套件仍来自隔离 debug 包。验后留册已停止，模拟器保持运行，其他应用数据未动。

## 限制与回退

实体手机未连接，真实手机升级、系统安装确认及设备特有行为未验收；未声称用户审美认可。正式release包已签名构建/验证，交互证据来自同源码隔离debug包。未更改备份格式、包名、签名、更新协议和媒体原件。版本回退须保留资料，不能卸载/清数据；安卓降级限制可能需要以更高versionCode重新构建旧源码。

最终新增代码差异经过Gitleaks目录模式扫描（Windows worktree不兼容WSL git模式，改为扫描导出的实际差异，非0字节伪通过）；无发现。签名配置和密钥只在仓库外通过环境引用。
