# 当前接续（2026-10-29）：下载记录清理体验改进

- **目标**：链接媒体下载记录上限为200项，成功收录到正式档案后仍占记录名额，用户只能逐项移除。新增「清理已收录记录」和确认说明，减少长期使用时的重复操作。
- **基线与范围**：main `d0c7576a9adda79b2f56c719e628e6e3b4c82a55`，任务分支 `feat/download-history-cleanup-20261029`。仅改下载记录管理、对应 UI 与回归测试，不修改 SQLite schema1、备份 format6、原件、签名、包名、发行流程和0.20已发布资产。
- **安全行为**：只清理当前资料代次中、目标档案已经正式保存的下载记录；尚未保存的草稿继续显示「继续完成收录」，待收录、下载中和其他代次任务均不清理。先提交下载记录索引，再撤销系统下载及清理其临时文件，不能因索引写入失败误删仍列出的文件。正式档案中的原件副本不会触碰。
- **验收计划**：Robolectric 场景覆盖下载→新建草稿→清理仍保留→正式保存→清理已完成记录、其他待收录文件与原件摘要仍在；还需针对本任务远端提交跑 Cloud development、Android checks、Secret checks，并核对 #50 缓存收益。CI/原生模拟器/实体手机均以实际回执为准，不预写通过。
- **发布边界**：本分支不预占0.21版本、不改已有正式 APK；合并与未来新版正式发行按已授权的同源校验、原签名覆盖升级和下载回读流程推进。

---

# 已完成接续（2026-10-29）：v0.20 已正式发行，绿灯收尾流程已改进

- **实际发行**：最新公开 [v0.20](https://github.com/h516df/liuzheba/releases/tag/v0.20) 于 2026-10-28 15:55 UTC 发布，应用源码 `5510326b14446a40189b9114e4e386f196eb47ea`；三资产为正式 universal APK、release-notes.json、SHA256.txt。原签名/包名/SQLite/备份及用户私有资料不受本轮流程改动影响；目标手机的实际安装及使用尚未验。
- **本轮已完成**：[PR #44](https://github.com/h516df/liuzheba/pull/44) 合并为 `135c82e2afd1b4f2491c6e86718bd6f905cdc4e5`，移除版本升级后硬编码断言和重复全量 CI。0.20 [PR #43](https://github.com/h516df/liuzheba/pull/43) 于 2026-10-28 12:08 UTC 四条检查全绿，15:17 UTC 合并，中间约 3 小时 10 分钟；无法从日志证明等待原因。
- **进一步改进**：用现有授权开发执行端负责 PR 全绿后的有界检查与合并；配备 `cloud/finish_green_pr.py` 作为可选用户身份终结脚本。GitHub Actions 自带 `GITHUB_TOKEN` 的自动合并会抑制新 main push 触发，可能破坏正式发布的精确主线门禁，因此**未启用**此类高权限 Actions 自动合并。新辅助脚本尚须后续自然产品 PR 的真实授权环境验证；单元测试不能等同于端到端验收。
- **接续**：先只读核对当前 GitHub main、PR、Actions 和最新 Release；本条是 2026-10-29 快照，下面历史中“当前”“待发布”仅代表当时状态，不能当实时事实。

---

# 当前接续：0.20 发现沉浸浏览与社媒图文下载（2026-10-28）

- **当前用户目标**：发现页应像刷短视频一样，上下切换整份档案，左右看媒体；档案页负责数据管理、搜索和编辑。用户反馈小红书图文链接下载失败，并要求一并审查其他社媒链接，完成后直接交付正式手机可覆盖更新的 APK。
- **起点与工作分支**：从已发布0.19/code20、main `997558b8e27c1d2ae27229c050e43b9239275394` 接续，分支 `feat/discovery-swipe-xhs-albums-20261028`。下一版0.20/code21，原包名/证书不变，SQLite schema1、备份format6和原件存储逻辑不改；不覆盖0.17–0.19资产。
- **变更**：发现首页重用现成的 `ArchiveFeed/FullPhotoFrame`，展示所有正式档案（含纯文字），全屏上下切换、内页左右翻媒体，底部沉浸双标签导航，当前发现视频默认静音自动播放；档案仍独立执行搜索整理。小红书短链含 `xhslink.cn` 跳转并复用网页会话、图集多字段提取，网页验证后把实际页面地址回传解析；非渐进式视频不能冒充普通下载；下载状态显示具体错误原因。
- **本地检查**：固定 `yt-dlp==2026.8.19` 的 `tools/tests/test_media_bridge.py` 7/7 passed；云端发行/签名策略23/23 passed。JDK21/SDK36/Gradle9.1隔离环境下新版 `compileDebugKotlin` passed；首次305项宿主全量304 passed、1失败是旧版 `notes.code==20` 断言，修正为21后该项定向复测 passed；发现阅读点击与返回位置定向复测 passed。随后同源 `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` passed，lint 0 errors、89 warnings、1 hint。**远端新源码全量Cloud development、Android device、Secret检查、原签名覆盖升级与v0.20正式Release仍待执行**；不能把旧0.19的通过记录视作新版通过。
- **下一步**：本地修复编译/测试问题→提交推送本独立分支、PR→同源Actions检查→受保护main合并与main checks→生产工作流原签名和0.16→0.20模拟器升级→公开三资产与匿名校验。实际手机尚未安装和验收，不得宣称已完成实体手机验证。

---

# 已完成历史：0.19 正式覆盖更新（2026-10-28，以下为当时发布前状态）

- **用户目标与设备起点**：用户指出仅保留Draft PR不能在真实手机试用；其当前手机仍安装0.17。要求将已完成的界面/交互改动公开发布到GitHub，使其使用原签名同包名APK直接覆盖升级，而不是安装debug、卸载或清除资料。
- **已核实基线**：仓库当前latest为正式v0.18/code19，main源`50287d5743a287c23153a4ed4b82be5d36447319`，其原证书和0.16→0.18模拟器升级验收见下方。PR [#41](https://github.com/h516df/liuzheba/pull/41)的0.18交互代码head`6341011c5600ecac42a498cc5935473507edf8a9`，四个同源Actions全部success，原生API35 8/8、宿主303/303，未在用户手机验收；此head没有发布APK。
- **当前0.19准备**：沿`ux/daily-flow-clarity`续接，版本改为0.19/code20，配套`release-notes.json`、`docs/releases/v0.19.md`及设置页说明断言；保持`dev.h516.shicang`、原证书、SQLite schema1、备份format6和媒体原件处理代码不变。新一轮精确代码测试、PR合并、main推送检查及受保护发行均尚未完成，不能把0.19候选说成已发行。
- **安全发行路径**：更新PR后待Cloud development、Android device checks、Secret checks同源通过，使用受保护主线正常合并，再等待精确main push checks通过；`production-release.yml`只接受main完整SHA。流水线必须完成原证书签名和官方0.16→0.19模拟器真实安装/导出资料比对，发布后三资产、latest/标签/摘要回读一致才告知用户下载。不会上传私有数据、替换0.17/0.18资产或把verification包当正式包。用户手机的0.17→0.19及其私人资料最终仍只能由用户实际确认。
- **检查进度**：发布策略Python 23/23通过，JSON解析与`git diff --check`通过；0.19源码`compileDebugKotlin -Pkotlin.incremental=false`成功，`EmbeddedReleaseNotesTest`与`ArchiveUiSnapshotTest.finalReleaseNotesOpenFromSettings`定向通过。当前机器的Kotlin增量缓存因更改BuildConfig触发重复声明假错误，关闭增量编译后通过；这不是发布阻塞理由，但最终依照干净的GitHub CI重新验证。远端检查待运行，通过后在此更新同源SHA和结果。已核实当前无v0.19 Release或Git标签。

---

# 当前接续：日常使用体验第一批（2026-10-28）

- **用户反馈与目标**：保留「留着吧」继续开发。重点不是增加功能，而是界面、交互和操作逻辑不顺手；本批先解决能在代码和合成截图中明确复现的入口歧义与多余步骤。尚未得到实体手机上的主观体验确认。
- **基线与分支**：基于`main`的`50287d5`，独立分支`ux/daily-flow-clarity`、[Draft PR #41](https://github.com/h516df/liuzheba/pull/41)，远端源码提交`4c849ea2c3b549565ddae78f1a8aee7ca8d1eb94`，树`092895769c78a1ded347a195c679e3505f2b0803`。保持原`applicationId`、正式签名、SQLite schema1、备份format6以及媒体原件处理机制，未修改正式版本号；本批不合并、不签名、不发布。
- **改动**：顶栏「收录」改「添加」，添加面板直接列出新建档案、相册、拍照、保存链接、下载链接媒体五种动作，取消链接二级面板；档案页排序与高级搜索分开，列表切换补充明确无障碍标签；仅一份档案时取消无效的刷新，多份时「换个顺序」必然改变排序；设置入口使用齿轮和「设置」名称；常规底部导航去掉大型悬浮胶囊；编辑页移除常驻草稿提示，但保留自动草稿保存、失败警示与返回后的“尚未更新档案”反馈。
- **检查**：已用项目隔离JDK21、SDK36和Gradle9.1编译`compileDebugKotlin`、`compileDebugAndroidTestKotlin`成功。定向Compose宿主回归32项通过；全量首次303项中302项通过、唯一旧常驻草稿提示断言不匹配，修正测试后最终全量重新运行，**303/303，59 suites，零失败/错误/跳过**。本地截图已检查常规底栏和添加面板。PR初次云端`Android checks`的`Existing feature validation`失败原因是Runner缺少Chaquopy需要的Python 3.12（尚未进入业务测试）；已为其与原生设备工作流都加固定Python3.12准备步骤，需检查新提交。API35设备与真实手机仍待验证，不能称作正式覆盖升级验收。
- **交付待办**：已推送并开Draft PR，等待同源 GitHub Actions 检查及 Android 原生验证；请以最新远端PR/提交为准，不用本地临时路径或历史0.18回执冒充本批结果。用户体验复核后再挑下一批编辑器和阅读流程，避免一次性大改导致难以定位回归。

---

# 0.18已发布与最终交接（2026-10-23）

- **正式0.18/code19已公开发布为latest**：[Release](https://github.com/h516df/liuzheba/releases/tag/v0.18)，源码/标签固定在`fcd10904f9e169753c116773788fe22f79e14bd0`，APK、更新说明、校验文件均匿名下载并核对大小/摘要。原包名`dev.h516.shicang`、原证书`67127caf5a760d51f11dc5a2fadcf44ddc30ce836fa14ffb9efd96c4b7e95a26`、arm64-v8a/x86_64、内外更新说明一致。APK SHA256`31b3c046fabd61933c5fbe1b8fa3f0bd6c7da680be3f6a3735d9279c6489910f`，105700012字节；此安装包与已发布0.17资产均不覆盖。
- **实现与体验**：发现在左、档案在右，共用卡片、阅读和沉浸查看器；纯文字档案可发现，档案/笔记索引集中编辑，草稿提示、媒体移除撤销、实际正文溢出展开、媒体目录原位返回和非当前页辅助功能隔离已实现。用户尚未认可视觉结果，不把测试通过称为整体产品已成熟。采用/舍弃理由及剩余边界见docs/PRODUCT_REWORK.md。
- **同源检查**：PR39开发源码`07d72dd144dc756f96fb213cb0af396ee7de939c`经正常保护分支squash整合为上述正式源码，完整树相同。正式main宿主300/300、59 suites、原生API35 8/8，零失败/错误/跳过；构建/lint通过，23项离线发布策略通过。具体运行、报告ZIP摘要和测试用例见docs/acceptance/v0.18/delivery.json，不把旧候选结果移作新提交。
- **正式发行闭环**：[生产工作流](https://github.com/h516df/liuzheba/actions/runs/39943830940)构建、原证书签名、真实系统覆盖升级与发布均success。一次性API35 x86_64模拟器通过正式0.16真实界面创建合成档案、文字、一张图片及横竖两段视频，经系统确认覆盖到0.18，再由真实界面导出完整备份对比，资料、顺序和原件摘要不变。不是读取用户手机私有资料；实体手机未验。
- **当前源码与资料保护**：原包名、证书、SQLite schema1及备份format6保持；本交接后续只改文档，app树与正式源码相同。旧分支、0.17标签和资产保留。旧版先导出完整备份，再下载正式APK直接覆盖安装，不卸载/清数据。
- **验收修复记录**：曾在重复push的工具栏截图中遇到4秒自动收起与截图计时竞争，已固定测试时钟并保留实际点击、显隐和返回断言，最终同源全量全部通过。图片JNI首次初始化预热仅在宿主测试fixture，不是产品侧绕过检查。
- **预览与回执**：[实际原生宿主界面](docs/acceptance/v0.18/README.md)、[完整交付回执](docs/acceptance/v0.18/delivery.json)。图片和媒体用例为合成资料，未上传私人照片；隐私扫描零命中。源码Apache-2.0，正式密钥只在GitHub android-signing环境。
- **协调**：产品源码批次和正式发布批次已完成，正式publication回执绑定标签源码`fcd10904f9e169753c116773788fe22f79e14bd0`；本次文档补充是独立任务，不把文档后续提交冒称APK构建来源。

---

# 当前接续：0.18产品体验改进（2026-10-23）

- 用户在0.17正式发布后指出UI、交互和功能像demo，明确发现应在左、档案在右，并要求由代理决定推进。本轮基线main为6d2f53750f9b4a7e45140e6be9d2a216e261e357；独立分支codex/product-experience-20261023，[PR39](https://github.com/h516df/liuzheba/pull/39)。历史PR不自动合并。
- 已实现共享ArchiveStoryCard/阅读/沉浸查看器，纯文字发现，集中编辑和笔记索引统一编辑，草稿提示、全部媒体移除撤销、实际文字溢出展开、缺失预览状态。上下换档案、左右换媒体、原比例视频、进度和返回状态继续保留。问题与参考取舍见docs/PRODUCT_REWORK.md。
- 版本0.18/code19，原包名/证书固定值、SQLite schema1、备份format6不变；原件与已发布0.17资产不覆盖。正式发布沿本会话已有明确授权与android-signing环境执行。
- 本地检查：97f6059ef80d99a53b28ce0338a863c754f16448的299/299宿主检查、59 suites、零失败/错误/跳过，debug/AndroidTest构建与lint完成（0 errors、87 warnings、1 hint）。随后发现媒体目录返回会退出查看器，补充原查看器/媒体位置恢复并追加用例，7项相关路径定向复验通过，构建/lint正在收尾；最终远端同源全量及原生继续执行。最初295项中的3个失败均已修复，不把中间失败写为通过。
- 首个候选48dd2413e6d2acb6a8eccb80e41fdc179807994a的API35原生运行39935933563成功，包含真实视频解码/定位/旋转/阅读返回、Media3编辑/取消、IME、SAF和混合分享8项；属于首个候选，不能代替后续修复最终源码检查。原生宿主FloatBuffer JNI首次初始化偶发崩溃，通过在截图fixture主线程预热Bitmap解决，最终定向未跳过，不当作Android产品崩溃。
- Python发布策略23项passed，隐私扫描248个文本文件零命中；合成URL user-info拒绝样例经核对添加精确privacy-ok说明，没有真实账号。实际Compose宿主渲染截图与来源见docs/acceptance/v0.18，不冒充物理设备。
- 9248636最终应用的PR侧300/300宿主与8/8原生均通过（PR合成merge4b0e383与源码完整树相同）；重复push侧300项中1项工具栏显隐失败，失败在截图与4秒自动收起竞争，已固定该交互测试时钟并保留实际点击/显隐/返回断言，截图类本地21项复验零失败/错误/跳过。不会只重跑掩盖失败；修复只改测试，最终同源检查重新执行。原生最终报告ZIPsha7a818a3e0f7baebbe6aa2d2b83156de2ff3ad13f74e504382e6b1c33361f8efa，PR宿主ZIPsha3a415dfbae5fb1aa21c08cb11b89987bd6789b97da481069262a470905598a22。
- 当前未宣称0.18已发行：最终同源CI、保护分支整合、正式签名、真实系统覆盖升级、Release和匿名资产校验仍按当前Actions/协调回执完成。旧正式0.17已发布事实及完整历史见下方。实体手机未验，用户视觉认可尚未获得。

---

# 当前发布与交接（2026-10-23）

- **0.17/code18已公开发布并为latest**：[Release](https://github.com/h516df/liuzheba/releases/tag/v0.17)，源码/标签固定在`6826b378061acb117eb08a2515d9070e792f1e3b`。APK、更新说明和SHA256三资产已逐份匿名下载并核对大小/摘要；APK原证书`67127caf5a760d51f11dc5a2fadcf44ddc30ce836fa14ffb9efd96c4b7e95a26`、正式包名、0.17/18、arm64-v8a/x86_64和内外更新说明一致。APK SHA256：`87faaa47e0a87ea025188130dbbccf88114fd3d8bc057bf6e299577bebb5e411`，105700012字节。已公开资产不覆盖，后续基础设施修复不改此标签。
- **同源检查**：main Cloud39920417507、Android39920417493、API35原生39920417485、Secret39920417494均success。下载报告确认292/292宿主、58 suites及8/8原生，零失败/错误/跳过；lint零错误、88条既有warning和1 hint。原生报告ZIP SHA256 `c3ea0fa216f1a8cb0264a13e9725456e9b130ca4aee651ce56ac63746580a071`；宿主报告ZIP SHA256 `758fa87d8847936b8b12f609aa3ec3f549d8a204b4df24fedeb326d8681cbdbb`。
- **正式升级**：[39921469593](https://github.com/h516df/liuzheba/actions/runs/39921469593)构建、原签名及覆盖升级均success；真实正式0.16界面创建合成档案/文字/一张图片/横竖两段视频，经系统确认覆盖安装，再由新版真实导出备份比较，资料/顺序/原件摘要完全一致。仅一次性API35模拟器，实体手机未验；没有正式私有目录读取、卸载、清数据或测试后门。
- **发布恢复已完成**：上述运行的publish步骤上传成功后，草稿按标签GET返回404，工作流保留failure事实。经Release ID421590291确认草稿target源码、原签名候选和三资产摘要一致，按ID公开原草稿并回读latest/标签/匿名下载；未替换任何资产。原签名候选和升级回执的SHA256都为上述87faaa47。完整publication及匿名下载/测试回执记录在codex-coordination-v1，发布批次已completed。
- **发布脚本修复**：新任务以已发布源码为只读输入，在独立分支修复云端发行；应用app树保持原样。分页检查含草稿的已占版本及已存在Git标签，创建草稿取得实际ID，上传后校验ID/源码/三资产，通过ID公开和回读；拒绝任何覆盖。版本正文使用对应`docs/releases/{tag}.md`。新增11项离线策略回归，含草稿标签404、来源变化、占位/权限失败、摘要不符及额外资产；连同既有12项共23项本地通过。GitHub PR/main检查以实际运行和协调回执为准，不把未执行检查写为通过。
- **接续约束**：正式安装包是Release资产，verification/dev附件不能覆盖正式版。升级前在旧版导出完整备份，直接覆盖安装，不卸载/清数据。源代码Apache-2.0；签名Secret只在android-signing环境。历史开发/签名分支保留。重构实现及成熟交互取舍见[docs/REBUILD.md](docs/REBUILD.md)。

## 本轮开发阶段记录（历史）

# 当前接续记录（2026-10-23 · 混合档案重构与正式发行进行中）

- 用户明确要求实施并发布GitHub；本轮授权覆盖历史仅准备不发行限制。
- 分支codex/archive-immersive-rebuild-20261023，基于PR33、完整包含PR31，已合入PR34原签名准备；旧分支保持原样。整合任务release-v017-cloud-20261022 generation2。
- 已实现统一正文与媒体事务保存、共用草稿、混合媒体筛选/排序/封面/多选、双入口导航、沉浸全文阅读、视频完整比例/手动横屏/进度与位置恢复、可恢复导入回执。详见docs/REBUILD.md。
- 本地宿主全量288/288通过（新增标签保存边界修复前）；修复后相关定向16/16通过，debug与androidTest构建通过；12项Python发布/签名拒绝与升级比较检查通过，全工作流actionlint1.7.7通过。lint已通过；远端首轮原生7项中6项通过，新增用例在收起视频后工具栏未重新出现处失败，已修复工具栏恢复及相邻视频播放状态归属，正复验。远端最终同源全量检查继续执行。新增合成横竖视频原生用例、通过正式旧版界面与系统安装器的覆盖升级验收、分离Secret的受控正式发行流水线。未执行设备和发布步骤不得称为通过。
- 原签名在GitHub android-signing环境已准备；不读取Secret值、不将密钥放进工作区。版本0.17/code18，schema1与备份format6不变。
- 下一步：本地检查通过后冻结提交/推送PR；检查远端同源CI，整合main；精确main检查成功后运行production-release.yml，回读正式Release与匿名资产。最终结果将在本记录更新。实体手机未验。

---

# 当前接续记录（2026-10-22 · v0.17 云端候选已构建）

- **用户指令与范围**：用户要求“做”，继续推进正式覆盖更新准备，不再让用户寻找签名配置。本批使用已授权的现有GitHub/Make连接，自行查明配置和构建候选；不把缺少原私钥解释成需要新签名或卸载原App。
- **分支 / PR**：独立整合分支`codex/release/v0.17-cloud-20261022` / [PR #33](https://github.com/h516df/liuzheba/pull/33)，基于PR #31最终head`9993abeba616b22764968935edc966114b5f206d`；已用祖先检查确认完整包含该成员。候选源码`df9bc2ba5ebe6b57ecf3f44b48956ab8bc5720f5`，根树`811c4db884ccac28cd32a7d48ffbad472a8084b8`，app树`850cc7a4ec5739ea6fd8f8a94347e54ea0fdcc0d`。实际CLI push并ls-remote读回成功；本轮没有合并main。
- **版本与候选**：0.17/code18；原正式包名、原证书公钥固定值、schema与备份格式保持。更新说明与设置页对应测试已更新，现有CI适配`codex/release/`以构建unsigned。[push release构建](https://github.com/h516df/liuzheba/actions/runs/39755897668)及[PR release构建](https://github.com/h516df/liuzheba/actions/runs/39756105135)success。APK为105641307字节、SHA256 `a08113ce69c789946f17f8ab4752ba187cd78455ea2bd276edee8009cd147da3`；unsigned，不是用户可安装的正式覆盖包。附件ID12416360775，已通过连接器下载并核对归档摘要，保存于`/workspace/scratch/liuzheba-v017-candidate`。gh下载在Azure临时重定向处403后，合法既有连接器下载成功；不混同签名权限问题。
- **检查**：下载XML核实release说明读取/设置展示2/2、零失败/错误/跳过；actionlint1.7.7语义校验与Python语法passed。新`tools/release/verify_apk.py`校验APK真实原证书、正式包名、递增code、说明版本、非debug及更新大小上限；使用真实旧正式APK、verification调试APK及本轮unsigned候选确认同版本、错误证书、未签名三种情况均被拒绝。三个负向检查passed不等于候选已具备升级资格；metadata_passed也不能冒充真实安装/保数据验收。
- **设备与CI**：[API35 device](https://github.com/h516df/liuzheba/actions/runs/39756105215)success，下载XML核实6/6、零失败/错误/跳过，只有verification隔离模拟器与合成媒体；[Secret checks](https://github.com/h516df/liuzheba/actions/runs/39756105362)success。PR合成merge`35aa3732c7291f0b1af34d28dc04f9cf988471f9`根树与候选811c4db一致。完整[Cloud development](https://github.com/h516df/liuzheba/actions/runs/39756105127)也已读回completed/success；本轮未下载其完整JVM XML重新计数，不把旧23项数量写成本版完整数量。
- **原正式基线**：已下载正式v0.16/code17，真实证书摘要与固定值`67127caf5a760d51f11dc5a2fadcf44ddc30ce836fa14ffb9efd96c4b7e95a26`一致，资产SHA256与GitHub发布元数据相符；基线APK及拒绝回执保存在`/workspace/scratch/liuzheba-release-inputs`。
- **签名核查的新结论**：通过现有Make GitHub OAuth管理员连接成功GET本仓库Actions Secrets，明确`total_count=0`；仓库environments=0，当前managed cloud无Secret绑定；按release/签名名称检索现有Make场景无匹配。此前GH_TOKEN的403仅是该连接限制，现已核实仓库配置为空，不再重复以403当作唯一阻塞说明。未读取或输出任何密钥值。
- **仍未完成**：原私钥在当前已接入渠道不可用，不能从旧APK/公钥恢复它。未生成替代正式密钥、未签名、未发布正式/预发行Release、未安装/卸载/清数据。原签名旧→新覆盖与实体手机保数据尚未验，正式latest仍为v0.16。候选及交接不是正式覆盖更新完成的声明。
- **协调与接续**：原工作成员`experience-refactor-20261022`冻结在ready；新整合任务`release-v017-cloud-20261022`、owner`codex-91839c4ac19c-release`、workspace`cloud-25a5cd9e84b3-release-v017`，同批已sealed。后续沿PR33、先读登记，不回写PR31或绕过成员冻结；签名阻塞时保存候选与交接并pause，原签名恢复后才能验证安装及发布。
- **详情**：[v0.17候选记录](docs/acceptance/v0.17/README.md)、[发布与覆盖校验](docs/RELEASING.md)。用户强调数据安全，数据保护23项继承证据仍见下节，不能把该宿主结果改称正式APK覆盖验收。

---

# 上一批记录（2026-10-22 · 导入失败与覆盖安装数据保护）

- **用户指令**：“推送吧先”；强调覆盖安装保留资料、导入图片/视频失败不丢数据。用户明确“你自己搞，别问我”，签名配置由代理自行核查，不再要求用户寻找位置或发送密钥正文。
- **源码已推送**：继续[草稿PR #31](https://github.com/h516df/liuzheba/pull/31) / `codex/experience-refactor-prep-20261021`。实现远端`e021dca8640369134594ba6718ac76a924cdf276`对应本地`2636a456fc2ca78ef501be30df8cc0540bdfa8ef`，根树均`14a5749fa015bd4357f7d4e05ab37fc8e862d909`，app树`8bb2132ea2c573b9ecd835699528432323ea5a29`。通过连接器expected-head更新，未合并main，未发正式更新。
- **修复**：分享/首页收录批次部分成功时，原来会清空整批incoming选择；现在只移除已成功关联的来源。失败项、复制期间新收到的媒体保留在当前待收录列表，新草稿的成功媒体继续落盘。当前列表仍属会话状态，不承诺临时URI与失败选择跨进程持久；外部相册来源始终不删除。
- **数据检查 passed**：冻结上述app树的`testDebugUnitTest --tests dev.h516.shicang.DirectMediaImportTest --tests dev.h516.shicang.LargeMediaStoreTest.declaredSixGBPlusOneRejectsWithoutTouchingSourceAndLongMetadataRoundTrips --tests dev.h516.shicang.LargeMediaStoreTest.interruptedImportDeletesOnlyItsPartialCopyAndKeepsExistingOriginal --tests dev.h516.shicang.BackupStoreTest`，使用`--no-daemon --max-workers=1 -I tools/verification.init.gradle`及既有JDK21/SDK36。23/23、3 suites、零失败/错误/跳过，3m32s；日志`/tmp/liuzheba-data-protection-check.log`。包含5个新增故障场景：部分成功重试不重复、新草稿部分成功、全部失败保留旧媒体/选择、复制期间新增输入不被清掉、既有PNG/视频/草稿在SQL关联失败并重开后内容摘要不变。已有用例另覆盖取消、读取中断、超限、旧备份保留与备份恢复。使用合成资料与宿主存储，不冒充原正式包覆盖升级/真机验收。
- **签名核查**：当前`SHICANG_SIGNING_PROPERTIES`/`SHICANG_KEYSTORE`未配置，本地约定配置不存在；managed cloud spec23显示`secrets=[]`、`runtime_variables=[]`。仓库`environments` API读回0个环境；读取Actions Secrets元数据返回HTTP403 `Resource not accessible by integration`，不能因此声称仓库Secrets为空。当前四个工作流只生成verification调试包或unsigned release，没有云端正式签名步骤；历史交接记载正式签名在旧本机构建。未访问旧电脑或把历史私有密钥搬进公开库。
- **覆盖安装边界**：公开最新正式版仍为v0.16/code17，原证书公钥摘要在`UpdateConfig.CERTIFICATE_SHA256`为`67127caf5a760d51f11dc5a2fadcf44ddc30ce836fa14ffb9efd96c4b7e95a26`。先前提供的APK是`dev.h516.shicang.verification`、Android Debug证书（901650a0…），不能覆盖原正式版。正式更新尚缺可用原签名及递增版本号/原签名旧→新安装保数据验收；没有换签名、卸载、清数据或发布不兼容包。不得把源码推送称作已推送正式更新。
- **下一步**：最终文档保存后将原协调任务转ready。继续时先核对PR与登记；保留原App和完整备份，在已授权且可访问的配置中恢复原签名通道后再做正式覆盖更新，当前不能靠证书公钥或APK反推出私钥。后续不反复询问用户密钥位置，准确报告403与已核实范围。

---

# 上一批记录（2026-10-22 · 发现页苹果风）

- **用户反馈**：查看当前发现页后明确“苹果风”，沿既有重构接续，不创建新产品或发布正式包。
- **分支 / PR**：继续 `codex/experience-refactor-prep-20261021` / [草稿 PR #31](https://github.com/h516df/liuzheba/pull/31)。远端实现 `83a2f66bf7c34e35490607c4dad690a4aa189f0f`，对应本地 `ca25847599e04e9680a5f08392f04be32aa79dbc`，完整根树均为 `acabb05c4ab2e3defc2892ac6e31bb925e7b4e31`，app树 `2ae294e0e7c23b76fbbf49923871988ebcaa43cf`。通过连接器expected-head更新并核对根树，不声称CLI push通过。
- **变化**：媒体延伸至浮动导航后方；减轻底栏、选中底色和边框；发现页使用深色导航，其他入口仍随主题；顶部张数独立居中，资料与更多为圆形按钮。底部档案名一点直达完整详情，右上角资料保留简要面板。视频进度工具为导航留出空间，空库沿用深色媒体画布与回档案入口。
- **复用与边界**：采用已读Orchard 0.3 / Apple HIG导航与材质规则，产品代码独立实现，未上传私有源码或Apple素材。当前是带透明度的表面与边框，未实现实时背景模糊或完整Liquid Glass。上下/左右翻看、随机预览、手动播放及返回原媒体保持原逻辑；普通全屏浏览保留文字返回入口。
- **验证 passed**：冻结实现后的定向复核12/12，8 suites、零失败/错误/跳过，日志`/tmp/liuzheba-discovery-verified-check.log`，完整命令为`bash ./gradlew --no-daemon --max-workers=1 -I tools/verification.init.gradle testDebugUnitTest --tests 'dev.h516.shicang.Discovery*UiTest' --tests dev.h516.shicang.UnifiedDiscoveryNoteUiTest --tests dev.h516.shicang.ArchiveUiSnapshotTest.libraryLight --tests dev.h516.shicang.ArchiveUiSnapshotTest.libraryDark --tests dev.h516.shicang.ArchiveUiSnapshotTest.mediaTapOpensViewerAndReturnsToSameFilteredCard`。JDK21/SDK36，5m18s成功，最终app树与83a2f66一致。中间回归暴露普通全屏返回文字入口缺失，已恢复原文字动作并重新编译复验，不把中间11/12写成最终通过。覆盖直接进详情/返回媒体、200%字号、空库出口、模拟播放器进度与导航不重叠、浅深档案页及原发现流程；模拟播放器仅验证几何，不证明编解码播放。
- **远端CI passed**：实现83a2f66的[Android checks](https://github.com/h516df/liuzheba/actions/runs/39748820874)、[Cloud development](https://github.com/h516df/liuzheba/actions/runs/39748820611)、[API35 device checks](https://github.com/h516df/liuzheba/actions/runs/39748820629)、[Secret checks](https://github.com/h516df/liuzheba/actions/runs/39748820600)均读回completed/success。本次未下载远端XML重新计数，下节267/267与6/6属于上一受测实现，不挪作本次精确数量。
- **预览**：实际Compose/Robolectric API35原生图形宿主，320dp窄屏、合成媒体；输出`app/build/outputs/unified-navigation/discovery-apple-*.png`，最终四张图与SHA256/来源清单另存`/workspace/scratch/liuzheba-review-20261022/discovery`。已目检代表浅色、深色200%、空库、播放器布局及档案页浅深色。静态图不能证明动画/真机体验；未签名、未安装正式包、未合并、未发布。
- **协调**：沿原任务`experience-refactor-20261022`、同一owner/workspace/generation接续；最终文档保存后转ready，准确head以PR和登记读回为准。后续先核对PR head、当前检查与登记，再接续剩余发现范围、全部媒体、重复导入、播放器专项。

---

# 上一批记录（2026-10-22 · 收录与页面重构实施）

- **目标**：用户要求开始重构，重点纠正使用逻辑、减少操作步骤、采用苹果式 UI/UX，并参考其 Orchard UI 组件库。首批围绕收录、详情追加、下载批次与内容入口落地。
- **暂停暂存**：本轮已恢复并整合先前 `paused-by-user-20261022-refactor-exploration` 暂存；后续从当前提交接续，不再次套用旧stash。
- **分支 / PR**：继续 `codex/experience-refactor-prep-20261021` / [PR #31](https://github.com/h516df/liuzheba/pull/31)，原三屏概念稿保留作历史参考。已将当前 main `0bfc3cea38d651bb1dfa9a2fa8977297e54f7c1d` 的文档更新纳入本地分支，不合并其他业务 PR。
- **源码与证据基线**：已通过连接器保存远端实现提交 `9611ebc81a2f99a9051501b9674b330ba8fda784`，根树 `cc8ab3b41d177dc43413c10836a6e54afef0aa3a`，app树 `0bd89633e09f7d661fcd368b25bdbb1fb8594d84`。已逐字节上传并核对本地 `1f717e3` 同一根树；连接器服务端提交元数据产生不同SHA，不宣称CLI push/receipt通过。GitHub PR测试合成提交 `439a240609277755d1328cad6517481102cd6922` 的根树也完全一致。最终回执文档提交只改文档，准确head以PR读回为准。
- **已实现**：可见档案/笔记/作者索引；统一收录面板；首页先选媒体再选位置、取消不建空草稿；编辑内相册/拍照直达；正式档案直接追加且不提交已有草稿的其他字段；同来源下载批次一次选目标、失败项保留；新建收录明确草稿状态并可从下载记录继续完成。已分离产品公共控件与内容索引组件，采用中性浅深色、蓝色动作、大标题、连续分段、输入/确认/导航一致规则。
- **复用边界**：已审查复用 #28 直接追加事务和提交后取消的状态对齐，没有整体合并 #28、预占发行版本或采用其历史测试为本轮结果。已读 Orchard 0.3@`5da1e128bf5f4cb9ad48b2adb1aa060d91047eb2` 的规则/代码/合成预览，公开产品按规范独立实现，不提交私有源码、生成文件、AAR或预览，不让公开源码构建依赖私有库。当前导航是实色材质，不标作实时模糊或完整 Liquid Glass。
- **远端验证 passed**：实现提交 `9611ebc` 的[Cloud development](https://github.com/h516df/liuzheba/actions/runs/39741852448)、[PR Android checks](https://github.com/h516df/liuzheba/actions/runs/39741852572)、[Push Android checks](https://github.com/h516df/liuzheba/actions/runs/39741846866)与[Secret checks](https://github.com/h516df/liuzheba/actions/runs/39741852334)全部成功。已下载并核对267/267、52 suites，零失败/错误/跳过；debug/application/androidTest构建与lint通过，lint为0 errors / 82 warnings / 1 hint。XML/lint报告ZIP SHA256 `57edc84ebe06e6f3339c4705f0b93f0f0f2c7f1bd54db62561e3eb65c5b2290b`。
- **原生模拟器 passed**：[API35 device run](https://github.com/h516df/liuzheba/actions/runs/39741852348)，读回XML核实6/6，零失败/错误/跳过。覆盖content-URI导入、播放定位与后台恢复、真实Media3 trim/crop/mask、取消导出、IME保存/取消与系统SAF备份往返；仅独立verification模拟器与合成媒体。报告ZIP SHA256 `dddde25aed2c554e9c54dc677119b16881d8345fa927e54db290629928a9f5ed`，合成merge树与受测源码一致。
- **本地验证**：早期定向回归44项暴露两个问题：收录弹层存在多个根节点导致截图失败，以及200%字号长确认的取消按钮不可见。已分别按顶层弹层取图、固定操作区/说明区滚动修复；最终CI同树267/267复验通过。本地 `bash cloud/check.sh` 的JVM部分也267/267通过；在远端最终实现已完整构建/lint通过后主动停止重复的本地dex构建（该本地全命令不是passed），保留XML副本。最终源树的宿主画面复核 `testDebugUnitTest --tests dev.h516.shicang.CollectionJourneyUiTest --tests dev.h516.shicang.UnifiedBackupLargeTextUiTest` 为5/5 passed；本地 `1f717e3` 根树与远端受测树完全一致。已目检浅深、空库、窄屏、大字和代表流程。临时合成预览与哈希清单保存在 `/workspace/scratch/liuzheba-review-20261022`，截图不上传公开GitHub，不把静态图说成动画/真机验收。实体手机与原正式签名覆盖升级 not run，未宣称用户认可外观。

- **工具链**：SDK36由现有 `.devcontainer/setup.sh` 初始化；完整JDK21由官方Temurin包取得并核对SHA256，使用系统CA信任。变量只有 `JAVA_HOME` / `ANDROID_HOME` / `SHICANG_BUILD_PYTHON` / `GRADLE_OPTS`；工具与缓存不提交，不假设新会话继承。Git CLI认证本轮恢复中失效后使用已连接GitHub工具继续原子登记与远端交付，不索取令牌或降低仓库权限。
- **方案、来源与待办**：[本轮实现说明](docs/research/experience-refactor-implementation-2026-10-22.md)。全部媒体/类型筛选、重复导入处理、发现范围、播放器/编辑专项和进一步批量整理尚未完成；现有功能入口保留。本轮不签名、不安装正式包、不合并主线、不发正式版。
- **协调**：专用 `codex-coordination-v1` 中 `experience-refactor-20261022`，本会话独占声明范围；下一会话先读登记、核对PR head并按协议续接，不能直接重做main。
- **下一步**：核对最终文档head与已通过实现的app树一致，查看本批实际合成画面；后续围绕使用反馈继续上述待办。续接提示：继续留着吧PR #31，先读AGENTS、HANDOFF、本轮实现说明并核对远端及协调登记。

---

## 之前的交接（历史保留）

# 当前接续记录（2026-10-21 · 统一通用入口）

- **目标**：适应用户经常新开云端 Codex 的方式；每个项目默认分支都指向同一通用工作流，并保留本地可执行简版，用户无需记启动语或分支名。
- **通用入口**：[vibe-ui-quality-kit/workflow](https://github.com/h516df/vibe-ui-quality-kit/blob/main/workflow/README.md)。简版修订 2026-10-21-r1；项目 AGENTS/CLAUDE 的特有约束继续适用。
- **本轮基线 / 分支**：`a9854163e4746a1285eadf799bdb3022b2370722` / `codex/shared-workflow-entry-20261021`。本轮只改规则与交接文档，不改业务源码、安装器、签名、数据、服务或发布权限。准确提交与 PR 以本次远端记录为准。
- **已完成**：统一入口、清理有效入口里的失效 agent-rules 引用、独立简版与自动发现相关任务的约定；保留下方项目历史和原检查入口。
- **验证**：本轮文档范围/简版一致性/项目约束保留/失效入口检查 passed；业务构建、设备、生产发布 not run。通用模板另在工具库验证。新 CI 结果以对应 PR 的实际运行记录为准。
- **当前任务发现**：下面是本轮开始前的未合并 PR 快照，不是建议合并清单。代理根据用户任务自行核对实时 head、依赖和相关分支交接；不要求用户逐个记住，也不能把最新 PR 自动当主线。
- **下一步**：新任务先读本仓库 AGENTS、云端说明与本记录，再按用户目标接续相关成果。完整私有规则暂不可读时先沿用本地简版；不能把存档视为账户级配置。

## 未合并任务快照（2026-10-21）

| PR | 任务分支 | 目标分支 | 读取时 head |
| --- | --- | --- | --- |
| [#31: design: 先预览档案、详情与收录 UI，再推进体验重构](https://github.com/h516df/liuzheba/pull/31) | `codex/experience-refactor-prep-20261021` | `main` | `c9af227e93c4aed638c01d02eef7ebf675ac040a` |
| [#28: 标签优先与一步媒体操作，降低自动保护和整库读写卡顿](https://github.com/h516df/liuzheba/pull/28) | `feat/core-journey-20261020` | `main` | `031d816e752bf26e6ab13ac63dc4579f91b6877e` |
| [#26: feat: 发现页按档案随机预览图片与视频](https://github.com/h516df/liuzheba/pull/26) | `feat/discovery-random-previews` | `main` | `222a3174287c910ba4057b2ce7b3a297cbfe9535` |
| [#25: feat: 独立长笔记、持久草稿及安全恢复](https://github.com/h516df/liuzheba/pull/25) | `feat/long-notes` | `main` | `682d30eac9e9009a58b56170e7b9ffedbe4afa79` |
| [#24: 新增直播录屏视频编辑：裁剪、固定遮挡与独立副本](https://github.com/h516df/liuzheba/pull/24) | `feat/video-editor-main` | `main` | `ff89075f94cecf8acf5747e52b378ba4bf32b9e2` |
| [#21: fix: 保留播放意图与前台一键更新](https://github.com/h516df/liuzheba/pull/21) | `fix/playback-foreground-update` | `feat/immersive-media` | `61ed05b30046bf3599ae8d82bb523a21757d7711` |
| [#20: feat: 媒体沉浸浏览与原位返回](https://github.com/h516df/liuzheba/pull/20) | `feat/immersive-media` | `feat/author-archive-workflow` | `776207aac3a7f6733dfd7f9ab180ed7d435aa737` |
| [#19: feat: 作者主页导入与统一档案编辑闭环](https://github.com/h516df/liuzheba/pull/19) | `feat/author-archive-workflow` | `feat/local-search-assistant` | `d7959c7f12f7f11978ae6ee5ea6835fa232ea3d2` |
| [#18: feat: 留着吧本机文字 AI、可解释搜索与定制规则](https://github.com/h516df/liuzheba/pull/18) | `feat/local-search-assistant` | `feat/automatic-local-protection` | `db8b2c1f0ab6c8ac48df8ee35e36ed74c751c9ad` |
| [#17: feat: 留册自动创建本机保护目录、校验与恢复](https://github.com/h516df/liuzheba/pull/17) | `feat/automatic-local-protection` | `fix/backup-restore-limits` | `7681690a70dfcb256c3ab6b107258494d6f68282` |
| [#16: fix: 导出前校验恢复容量上限并保留已有备份](https://github.com/h516df/liuzheba/pull/16) | `fix/backup-restore-limits` | `main` | `a02b0efb8a10eac097bc66943fd6e7c006ee3ead` |

## 之前的交接（历史保留）

# 当前接续记录（2026-10-21）

本节是会话接续入口；长期规则见 [AGENTS.md](AGENTS.md)，工具链和检查命令见 [CLOUD_DEVELOPMENT.md](CLOUD_DEVELOPMENT.md)。后续任务更新本节，下面已有历史保留。

- **本轮目标**：用户要求重构准备和网上灵感，并询问 GitHub 发布能力；随后明确“整体布局混乱、功能难找”，喜欢苹果，要求先做 UI 看效果。当前优先交付视觉稿，不擅自进入全面代码重写。
- **核对的主线基线**：研究和视觉稿基于 `a9854163e4746a1285eadf799bdb3022b2370722`，源码为 0.16 / code17。收尾时远端 main 已到 `0bfc3cea38d651bb1dfa9a2fa8977297e54f7c1d`，本轮 fetch 后确认仅 AGENTS / CLOUD_DEVELOPMENT / HANDOFF 三份通用工作流文档变化；没有把别的任务合并进本分支。已读新增的通用入口与其 workflow/README.md，继续时重新核对。
- **本轮分支 / PR**：`codex/experience-refactor-prep-20261021` / [草稿 PR #31](https://github.com/h516df/liuzheba/pull/31)。设计交付提交 `c9af227e93c4aed638c01d02eef7ebf675ac040a` 已推送并 ls-remote 核对；收尾反馈以本次后续提交为准。不改其他任务分支，不合并、不发版。
- **已完成**：云端 SDK 初始化、Gradle 9.1.0/JDK21 启动核对；读取 AGENTS / CLAUDE / CLOUD_DEVELOPMENT / HANDOFF 与相关规则；核实源码关键流程，检索并阅读 Eagle、mymind、Anytype、Ente 官方说明；形成 [体验重构准备](docs/research/experience-refactor-2026-10-21.md)，包含问题证据、参考取舍、目标路径、P0–P4、验收和发布边界。
- **视觉稿与反馈**：[档案/详情/收录](docs/design/ui-preview-2026-10-21.png)，由图像工具生成，使用虚构物品示例。用户看后反馈“还行”，并要求“先收尾”；记录为首版方向初步可接受，不是最终定稿或全面实施授权。视觉稿不是 Android 实际截图或交互原型。实施时仍遵循 Android 行为；发现/我的尚未出视觉稿。
- **当前停点 / 待完成**：按用户要求收尾，停在研究与三屏视觉稿阶段。后续继续时围绕“整体布局混乱、功能难找”，按 P0 清点入口、P1 原生小样，再审查复用 #28 并打通流程。业务工作未因研究文档完成而完成。
- **已有候选核对**：#28 为 open/draft，head `031d816e752bf26e6ab13ac63dc4579f91b6877e`。GitHub 返回 mergeable false，但 `git merge-tree --write-tree origin/main origin/feat/core-journey-20261020` 成功，树 `e0ae6a1f4af0072dd8a6d8b32a7183991f1b539f`；仅对象模拟，不是实际合并或验收。#24/#25/#26 原 head 不是 main 祖先，而主线已有对应功能，需按文件/行为核对，不能因旧 PR 还开着就重新实施。
- **验证范围**：设计提交 `c9af227` 的 `git diff --cached --check`、10 个本地文档/图片链接、代码围栏、HANDOFF 历史保留和 PNG 格式/1536×1024 尺寸检查 **passed**，远端 SHA 核对 **passed**；收尾提交复核文档与远端。业务测试/完整 APK 构建/原生/实体手机/正式发布均 **not run**；原历史测试不可重标成本轮结果。
- **配置与阻塞**：开发入口 `source .cloud/env.sh`、`bash cloud/check.sh`；工具通过 `.devcontainer/setup.sh` 恢复。GitHub 连接返回 admin/push，云端 gh 认证和 Release 列表读取成功；源码写入以实际推送和 SHA 读回为准。当前未注入原正式签名 `SHICANG_SIGNING_PROPERTIES` / `SHICANG_KEYSTORE`，申请/配置位置见 `docs/RELEASING.md` 与云端环境 Secrets；不读取或迁移旧电脑密钥，不把 unsigned/debug APK 发布为官方包。
- **下一步与续接提示**：用户下次要求继续留着吧时，代理自行找到 #31，读取本节、视觉稿及研究方案；沿用首版初步可接受的方向，再依据具体请求细化或实现。重新核对 main/#28，复核“追加媒体、名称建档、浏览返回、找回内容”；不要要求用户记住分支名，不自动合并旧 PR，不把概念稿称作体验已改善。

## 打开的 PR 快照

以下仅为 2026-10-21 读取到的列表，不判断是否仍需实施、能否合并，或是否已由其他提交包含；不批量合并、关闭或重复搬运。

| PR | 分支 | 读取时 head | 状态 |
| --- | --- | --- | --- |
| [#28: 标签优先与一步媒体操作，降低自动保护和整库读写卡顿](https://github.com/h516df/liuzheba/pull/28) | `feat/core-journey-20261020` | `031d816e752bf26e6ab13ac63dc4579f91b6877e` | 草稿 |
| [#26: feat: 发现页按档案随机预览图片与视频](https://github.com/h516df/liuzheba/pull/26) | `feat/discovery-random-previews` | `222a3174287c910ba4057b2ce7b3a297cbfe9535` | 草稿 |
| [#25: feat: 独立长笔记、持久草稿及安全恢复](https://github.com/h516df/liuzheba/pull/25) | `feat/long-notes` | `682d30eac9e9009a58b56170e7b9ffedbe4afa79` | 草稿 |
| [#24: 新增直播录屏视频编辑：裁剪、固定遮挡与独立副本](https://github.com/h516df/liuzheba/pull/24) | `feat/video-editor-main` | `ff89075f94cecf8acf5747e52b378ba4bf32b9e2` | 草稿 |
| [#21: fix: 保留播放意图与前台一键更新](https://github.com/h516df/liuzheba/pull/21) | `fix/playback-foreground-update` | `61ed05b30046bf3599ae8d82bb523a21757d7711` | 草稿 |
| [#20: feat: 媒体沉浸浏览与原位返回](https://github.com/h516df/liuzheba/pull/20) | `feat/immersive-media` | `776207aac3a7f6733dfd7f9ab180ed7d435aa737` | 草稿 |
| [#19: feat: 作者主页导入与统一档案编辑闭环](https://github.com/h516df/liuzheba/pull/19) | `feat/author-archive-workflow` | `d7959c7f12f7f11978ae6ee5ea6835fa232ea3d2` | 草稿 |
| [#18: feat: 留着吧本机文字 AI、可解释搜索与定制规则](https://github.com/h516df/liuzheba/pull/18) | `feat/local-search-assistant` | `db8b2c1f0ab6c8ac48df8ee35e36ed74c751c9ad` | 草稿 |
| [#17: feat: 留册自动创建本机保护目录、校验与恢复](https://github.com/h516df/liuzheba/pull/17) | `feat/automatic-local-protection` | `7681690a70dfcb256c3ab6b107258494d6f68282` | 草稿 |
| [#16: fix: 导出前校验恢复容量上限并保留已有备份](https://github.com/h516df/liuzheba/pull/16) | `fix/backup-restore-limits` | `a02b0efb8a10eac097bc66943fd6e7c006ee3ead` | 草稿 |

## 下一次交接时更新

请在本节写清本轮目标、完成/待办、工作分支和 PR、所验证的提交 SHA、实际命令与 passed / failed / not run、阻塞、下一步；代码和交接一起推送，再给用户已核实的远端提交链接。未合并时须交代指定分支，不能让新会话只读 main。

---

## 原有交接历史

以下原文保留；其中旧路径、候选版本与测试结论需按日期和源码重新核对。



## 2026-10-18 用户纠正后的继续任务（实施中）

播放器使用统一浮层、长按2倍速松手恢复、双击和按钮快退快进10秒、滑动进度；我的列表左滑移入可恢复回收站；Android文本分享识别抖音/小红书/快手主页、直播间、作品并预填编辑草稿；保持本地离线和资料结构。

同一写手分支 agent/codex/modern-experience-20261018；上一版本不覆盖。通过针对性逻辑和原生交互回归后再发新版本，实体手机未连接，不冒称真机已验。

当前待发布版本 0.9，证据见 docs/acceptance/v0.9/README.md。用户2026-10-18已授权既有公开/私有仓库连续推送、PR、审查合并及验后发版。

## 2026-10-18 成长与探索扩展
用户要求三个App增加游戏化、记录感和成就感；足迹收拢重复诊断入口，探索页显示可核验的地区探索度，并提供后台稳定设置与最近任务隐藏选项。当前Codex单写手，沿用独立工作区；本轮交付成长页、等级/徽章/周目标/活跃日历、本机初次安装与前台使用统计。奖励绑定真实记录，升级保留旧数据；未得到村级边界不能伪称村级百分比，不承诺系统强制停止后永不退出。真实UI、持久化与原有功能回归后发布递增版本。

2026-10-18 用户新增审美约束：苹果式克制、留白、少文字，不铺满。成长页改为稀疏数字布局，徽章墙与日历按需展开，统计规则收进说明。

2026-10-18 用户要求换名称和图标，当前采用「留册」；仅品牌显示与图标变化，包名、仓库、更新前缀及持久化标识不变。足迹涂色保留原名和图标。

第三轮实现及验证见 docs/verification/v0.10。最终正式包 SHA256 33fc264682f0415116f35323c0745e21000b6bf48cc011756a86e091eb1533ec；35 JVM、14 原生场景与大字体复验，0.9->0.10覆盖升级档案保留。API24/25图标提供回退资源，设备未跑；无真机声称。

第四轮v0.11按用户的少字、小白友好与原神质感方向调整。证据见docs/acceptance/v0.11，未改已有数据格式。

## 2026-10-18 第五轮：内容优先原生改版（Issue #10）

用户要求不要停在苹果/原神的表面装饰；沿明确偏好自主完成，无需中途审美确认。当前分支 issue-10-content-first-ui，v0.12/code13；未宣称用户认可视觉。打开先到档案墙，发现保留沉浸浏览；档案标题/封面进详情，媒体按钮进入原查看器，列表缩略图也是媒体入口。封面与列表均保留左滑回收确认。分类标签前移，管理页可搜索；成长移至设置，以留念为主，小目标按需展开。设置的备份与恢复保留完整隐私确认。

未改资料格式、签名、公开更新协议或媒体/播放器核心。图片 Pager 每页始终为稳定 ZoomPhoto，新增动画只作用于浏览工具浮层与设置展开。ContentFirstUiTest 验证真实点击/滑动后的落盘删除恢复；原有验收适配新导航。root 负责分配构建/AVD资源与最终复核合并发布，子代理不自启第二个AVD。最终证据写入 docs/acceptance/v0.12；实体手机当前未连，不能声称目标手机验收。

第五轮验收完成：36 JVM、8条原生路径和小屏深色1.3倍字2条路径通过；69图片帧可见率1.0。修复完整分享链接不必要等待网络。正式0.12包绑定58171a2，SHA256见docs/acceptance/v0.12/build.json；旧d52426包不可发。实体手机与正式系统升级仍未测，root继续独立复核/合并/发布。


## 2026-10-20 留着吧 0.15/code16 正式交付

隔离工作区 D:/Workspaces/Codex/2026-10-20/task-6/source，分支 release/v0.15。基于 PR21 exact head 61ed05b30046bf3599ae8d82bb523a21757d7711/tree 2ddcf6d3036bded81673ff0db85b06763c147edf；远端 main 3d9238334d338d89d0f54e016b669b75dd71a841 为真实祖先，PR16→21 无重复 cherry-pick。

既有 0.14/code15 已发布，因此递增0.15/code16。复用 PR19 核心161、PR20沉浸20、PR21播放器/更新37及相应APK/lint证据，PR21 Android run39370417556。最终输入仅补说明解析和设置打开更新页；release/ 分支云端只做这两项及 unsigned release，本机沿用仓库外正式密钥签名。首次本机构建在执行通道断连时终止，无APK，未作为通过证据。

发布仅原 h516df/liuzheba（ID1422110562，public），不操作统一分发仓库。现有数据目录、原包名/证书、旧备份、历史留册自动保护目录保持；格式5仍读取1–4，旧App不能读取新格式。平台匿名成功率、设备音频/旋转/安装及升级保数据未实机验收。不安装、不清数据、不迁移或新增签名密钥。
