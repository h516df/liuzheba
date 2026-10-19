# 留册 0.13 验证与发布

本轮将发现页的上下手势回调换为稳定纵向分页，提前准备邻近档案；增加图片/视频原件保存、多选分享、系统媒体分享收录和手机本地链接解析/后台下载。资料与备份格式保持不变。

## 构建绑定

正式 APK 由生产源码 `f3946b5949009a7d5a9d082dccb413bf7fbae694` 构建。其后提交仅改测试和说明，未改生产 Kotlin、Python、Manifest、Gradle 或 App 资源。详细包大小、SHA256、签名、架构及测试提交见 [build.json](build.json)。0.13 / code14，原包名、原正式证书；正式构建完成 lintVital、R8 和 apksigner 校验。

## 验证结果

- 40 项 JVM 用例通过，包括随机序列、链接/媒体类型与大小、下载头筛选，以及原有资料、备份、分享与更新逻辑。
- 3 项 Python 离线用例通过：合成小红书图集保持顺序且不误取缩略图；合成抖音图集不误取视频封面；选择带声音的完整视频格式并过滤认证请求头。
- 7 项不同原生用例通过：MediaExperienceTest 的两个用例，以及 PhotoSwipeTest、VideoUiTest、MediaCollectionUiTest、ContentFirstUiTest、ViewerLockTest。原始回执见同目录 txt 文件。
- 上下拖动在松手前已显示下一份档案；17 帧最低可见率 1.0。见 [手指仍按下时的画面](finger-still-down.png) 和 [结果](result.txt)。首轮截图混入系统全屏提示，已修正探针并重跑，提交的是去掉提示后的画面。
- 真实点击查看器保存图片后，读回系统 MediaStore 文件验证 SHA256 与原件一致；图片/视频两个系统后台下载任务完成，重新创建下载仓库后仍可查询、读回并导入。
- 深色主题、font_scale 1.3：真实 ACTION_SEND_MULTIPLE 收录合成图片与视频、编辑保存重读；两类原件批量导出、重复保存不重复生成文件，读回哈希一致。档案墙操作、媒体入口、回收恢复和应用锁回归通过。截图等待窗口动画结束后重新采集，见 [下载页](downloads-dark-large.png)、[编辑页](shared-editor-dark.png)、[空库](empty-dark.png)。测试后字号已恢复。

## 正式覆盖升级

使用本轮专用全新 AVD，在正式 0.12/code13 中通过真实界面创建 `Upgrade-v013-fixture`，再 `adb install -r` 安装已签名的 0.13/code14。没有卸载或清数据；重开后从档案墙进入详情，名称保留。见 [回执](official-upgrade.json)、[升级前](official-upgrade-before.png)、[升级后](official-upgrade-after.png)、[详情](official-upgrade-detail.png)。这项测试没有触碰原开发模拟器中的正式包资料。

正式 R8 安装包的本地解析入口也已通过真实界面验证：公开视频页面解析后显示“下载所选 2 项”，没有启动下载。见 [回执](official-release-extraction.json) 和 [预览截图](official-release-extraction.png)。

## 范围与限制

实体手机未连接，以上是 API36 x86_64 模拟器和源码逻辑验证。新版 Python 运行时仅含 arm64-v8a / x86_64，需要 64 位设备、Android 7.0 起；安装包约 45 MB。平台登录、风控、作品链接有效期及接口变化仍会影响解析；未把过期小红书样例的失败说成平台下载通过。可在内置网页中完成验证后重试，或从平台/下载器分享媒体到留册。不承诺主页批量抓取、直播录制或所有网站均支持。

下载由系统管理，完成后用户选择档案收录并保存，后台任务不覆盖编辑中的档案。下载记录不随档案备份恢复；原件仍走既有导入、校验与备份路径。第三方版本与许可见根目录 THIRD_PARTY_NOTICES.md。
