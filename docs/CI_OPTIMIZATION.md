# 开发速度与质量门禁：2026-10-31 实施记录

## 问题与基线

v0.22 主分支源码 `3b4123c4890e7108d884b5f1c64b23b88b3d045b`：Cloud development [41177417026](https://github.com/h516df/liuzheba/actions/runs/41177417026) 从创建到完成约9分25秒；Android device [41177416468](https://github.com/h516df/liuzheba/actions/runs/41177416468) 约5分28秒；Android checks 约2分32秒。正式 [production-release 41178310812](https://github.com/h516df/liuzheba/actions/runs/41178310812) 从 build 开始到 publish 约9分25秒。PR、main push、production release 彼此串行等待，**不能相加并声称均为编译耗时**。

只修改发布辅助工具/说明的 PR #62 仍触发完整版 Android CI；其 squash main `960e917c84f0176067c3ee0deea1947332647f15` 的 API35 设备测试首次和重跑均出现 `Pixel Launcher isn't responding` 遮挡，8 个 UI 测试中5个报「搜索档案」超时，非业务断言明确失败证据。该问题跟踪 [#63](https://github.com/h516df/liuzheba/issues/63)，不以重跑到碰巧成功代替根因判断。

## 本次改动与安全边界

1. `cloud/ci_scope.py` 统一两个 PR 工作流的变更分类；允许名单只含确定不影响 APK 的文档和用户态发布辅助工具。只改 Python 社媒桥接继续离线测试；改 release notes、版本、App、Gradle、CI、本脚本或出现不可解释的 diff 时一律走完整构建。专用 `cloud/test_ci_scope.py` 覆盖边界和 Git 异常。
2. Android API35 仍使用 `google_apis` 镜像及相同 8 个合成设备用例，模拟器调整至4核心/4 GiB；启动前检测 `sys.boot_completed`，测试失败时收集限定长度的系统 ANR/崩溃事件，保留真实失败退出码。**这只是候选稳定性措施**，只有 GitHub 原生设备检查成功且后续复现稳定才能声明问题已解决。
3. `tools/release/dispatch_verified.py` 可在已有明确发布授权的用户 `gh` 会话中 `--dispatch --wait-seconds 1200`，自动核对原生产运行结果。`tools/release/observe_verified.py` 允许等待超时或断线后只读恢复同一个源码对应的运行，不能再次派发或修改已存在 Release。观察必须核对四个作业、公开稳定发行、准确源码、3份资产大小和 SHA256；手机实装仍为 not_tested。

main push、正式发布、签名隔离、原包名/证书、v0.16→新 APK 数据保留验证完全不变。曾经发布的 v0.22 文件不更新或重签。

## 应用与验收

- 本地廉价检查：`python3 -m unittest discover -s cloud -p 'test_*.py'`；`bash -n cloud/device_acceptance.sh`；`git diff --check`。
- GitHub PR：核对快车道脚本输出（`ci_scope`）是否符合改动实际范围，并核对所需 PR 检查通过；本 PR 自身修改 CI 文件，**必须完整执行**。
- GitHub main：合并精确受测源码后，四组 main push 必须 success，特别核对设备 ANR 是否真的消失。
- 效率对比：用相同基线的一次真实 docs/maintenance-only PR 记录最快和最慢检查耗时；不要把单机本地单测 0.05 秒冒充 CI 端到端耗时。
- 未进行正式新版本发布；也未进行目标手机安装、真实小红书/抖音/Instagram 作品下载验证。

## 第一次端到端 CI 结果（2026-10-31）

- [PR #64](https://github.com/h516df/liuzheba/pull/64) `304b9a97f9b2905b1135f7d9760860e570d5f2bb` 的四组 PR 检查全部 success（Cloud 41188994570、Android 41188994571、device 41188994568、Secret 41188994586）。实际 Cloud 日志确认 `ci_scope=full`、`changed_files=14`，符合本 PR 修改 CI 分类器必须完整验收的预期。
- squash 后 main `7e8326b0c5ea5c69f039ac2abb9ec2fe1e9d689f` 的 Cloud [41189449208](https://github.com/h516df/liuzheba/actions/runs/41189449208)、Android [41189449215](https://github.com/h516df/liuzheba/actions/runs/41189449215)、device [41189449216](https://github.com/h516df/liuzheba/actions/runs/41189449216) 和 Secret [41189449210](https://github.com/h516df/liuzheba/actions/runs/41189449210) 均 success。
- PR 与 main 各完成一次 API35 原生设备验收且没有再出现 Pixel Launcher ANR，说明扩大资源配置后的两次观测有效。两次绿灯不证明系统级 ANR 已永远消失；故障再次出现时以收集的系统事件调查。没有修改/忽略原生验收用例。
- 新快车道尚未由独立纯维护 PR 证明实际耗时。本次后续的交接文档改动只触及 `HANDOFF.md` 与本文件，恰属于保守允许名单；其真实 PR 工作流需要展示 `ci_scope=maintenance` 且没有 Android Gradle 构建，才能记为端到端提效通过。

## 维护快车道与第三轮设备异常补充

- [PR #65](https://github.com/h516df/liuzheba/pull/65) 仅修改 `HANDOFF.md` 与本文件，GitHub Cloud/Android 日志均为 `ci_scope=maintenance`、`changed_files=2`；Cloud 41189973646、Android 41189973624、Secret 41189973603 全 success，最慢 Cloud 从 09:45:21 UTC 创建到 09:45:34 UTC 完成，**约13秒**，且没有运行 Android Gradle。此提速只适用于严格的维护允许名单。
- 该维护 PR 合入后的 main `161664d` 首次发现一项已有的 Compose 测试入口偶发失败（Cloud 41190024539 330项中1失败），已在 [PR #66](https://github.com/h516df/liuzheba/pull/66) 第二版改为 `useUnmergedTree=true` 与等待真实卡片标题，修正版 PR Cloud 41191105217 success，main `0899341` Cloud 41191628016 success。测试没有被删除/跳过。
- main `0899341` 的 API35 device [41191628221](https://github.com/h516df/liuzheba/actions/runs/41191628221) 又暴露另一种独立环境失败：emulator 启动时报 `Not enough space to create userdata partition`，实际可用约6169 MB、需要约7373 MB，设备根本未连接，未执行一项 App 原生用例；不能归类为 Launcher ANR 或产品回归。下一次修正保留4GiB RAM，明确通过 pinned Android emulator runner 的 `disk-size: 3072M` 限制虚拟数据分区，待 GitHub PR/main 真实设备结果判定有效性。


## JVM 短生命周期测试调优实测（2026-10-31）

- 根因候选：`app/build.gradle.kts` 为避免 Robolectric 原生 SQLite/graphics 跨测试类残留而固定 `forkEvery=1`；本仓库约49个 Kotlin 测试源文件，启动多个短期 JVM 成为真实开销。不能为速度直接移除进程隔离。
- 三路并发 [PR #69](https://github.com/h516df/liuzheba/pull/69) 保留测试但 Cloud 从421秒至422秒、Gradle步骤从363秒至387秒，四组检查虽全成功仍**不合并**。
- 两路并发保留，[PR #70](https://github.com/h516df/liuzheba/pull/70) 仅给 GitHub CI 的测试 JVM 加 `-XX:TieredStopAtLevel=1`。PR 的四组检查 success，main `34c236554551c67e400a3308ae8a2a0e45606aa6` 的四组 push 也全部 success。本地弱电脑仍1 worker，生产 APK 不使用此标志。

| 同类完整检查 | Cloud 端到端 | `Check this revision` | Gradle任务 |
| --- | ---: | ---: | --- |
| 旧 main `d83b680` · [41194031404](https://github.com/h516df/liuzheba/actions/runs/41194031404) | 421 s | 363 s | 53 executed / 44 cached |
| PR #70 `c289cbd` · [41203216673](https://github.com/h516df/liuzheba/actions/runs/41203216673) | 322 s | 283 s | 53 executed / 44 cached |
| 新 main `34c2365` · [41203704055](https://github.com/h516df/liuzheba/actions/runs/41203704055) | 396 s | 340 s | 53 executed / 44 cached |

**解释边界**：PR 的24%端到端改善没有在第一次main上等幅复现，main仅约6%。原基线从 `testDebugUnitTest` 至 `lintDebug` 的日志间隔约273秒，PR约228秒，新main约283秒，不能在只有一次A/B和一次main样本时推断JIT参数已经稳定减少测试时间。保留完整回归与隔离；未来正常业务 main push 继续积累可比样本，若反复变慢再决定撤销。仅发布用户态 CI 改动，不重发 v0.22、签名或迁移数据。


## 预热实验的第二次 main 证据与保守回退（2026-10-31）

[PR #71](https://github.com/h516df/liuzheba/pull/71) 只有 `HANDOFF.md` 与本文件，维护快车道 Cloud [41204400365](https://github.com/h516df/liuzheba/actions/runs/41204400365) 23秒 success、未执行 Android Gradle；该 PR 的 Android、Secret 也 success。合并后 main `40c91cc9f70098dd5d794f3c31cbdbeb7ec15ada` 四组 push 中 [Cloud 41204487496](https://github.com/h516df/liuzheba/actions/runs/41204487496) **failure**，另外三组 success。

Cloud 日志明确是 **330 个 JVM/Compose 测试中一个 `ProductJourneyUiTest.discoveryFeedReturnsToSameMediaAfterReadingAndEditing` 在 `ProductJourneyUiTest.kt:52` 抛 `ComposeTimeoutException`**，并非编译/JVM 参数非法。此前此测试及同夹具测试曾有时序性故障，不能把这一次归因于新JIT参数，亦不能忽略失败或继续声称稳定提速24%。为最小化变量，`fix/revert-jit-warmup-after-flake-20261031` 候选撤回仅CI使用的 `-XX:TieredStopAtLevel=1`，恢复原2个测试fork/每类新JVM，保留330项完整回归和所有其它检查。需在新 PR/main 精确提交复验；如故障仍存在，则独立定位 Compose 异步回流/可见计数，不用减少测试覆盖来换绿灯。
