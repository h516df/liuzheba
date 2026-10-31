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
