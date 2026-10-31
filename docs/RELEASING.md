# 构建、签名与更新

## 调试构建

`testDebugUnitTest assembleDebug` 不需要正式密钥。调试签名与官方正式签名不同，不能覆盖正式版。并排测试使用 `tools/verification.init.gradle` 的 `.verification` 独立包；不要为调试卸载存有资料的正式版。

## 正式签名

公开仓库不包含正式密钥、密码或旧私有历史。维护者在仓库外保存密钥，另留离线备份。构建时通过 `SHICANG_SIGNING_PROPERTIES` 指向包含 `storePassword` 和 `keyPassword` 的本地配置，通过 `SHICANG_KEYSTORE` 指向正式 JKS，alias 为 `shicang`。不要打印或提交这些内容。也支持本地忽略的 `keystore.properties` 和 `keystore/shicang.jks`，但官方维护推荐使用仓库外路径。

没有签名配置时，`assembleRelease` 生成 unsigned APK，不能发布为官方安装包。正式更新必须沿用相同签名并递增 versionCode。安装前核对大小、SHA256、包名、版本、签名及架构；异常时保留旧版本，不通过卸载解决签名冲突。

## 云端候选包与覆盖安装校验

`codex/release/`与既有`release/`分支会构建unsigned候选包并保留Actions附件。unsigned包不是可安装的正式更新；只有沿原正式证书签名后，才能进入发布步骤。正式密钥从仓库外已有安全配置注入，不上传源码或构建附件。v0.17使用code18，仍沿用正式包名与原证书公钥固定值。

签名后的候选包先执行：

```sh
python3 tools/release/verify_apk.py \
  --candidate /secure-output/app-release.apk \
  --previous /verified-input/shicang-v0.16-universal.apk \
  --sdk "$ANDROID_HOME" \
  --receipt /temporary-output/upgrade-apk-check.json
```

脚本核对真实APK签名、原证书公钥摘要、正式包名、递增versionCode、当前更新说明版本、非debug及更新大小上限；失败写入blocked回执并退出非零，不安装、不卸载、不清数据、不发布。metadata_passed只证明安装包身份元数据，不能代替旧版→新版安装及资料保留验收。当前独立verification测试包即使可安装，也不能替代该校验。

2026-10-23：原正式签名已在受保护的GitHub Environment `android-signing` 中准备并验证。开发工作区不绑定签名Secret。此前2026-10-22签名配置为空是历史状态。

## 公开更新

v0.5 起匿名读取 `h516df/liuzheba` 的公开稳定 Release，不需要 GitHub 登录，不发送旧版更新令牌。检查更新时清理旧更新授权存储，不读取或解密旧令牌，不影响档案数据。草稿和预发布版本不进入自动更新。

旧版使用私有仓库授权；仓库重建后可能无法继续检查更新。首次迁移应从公开 Release 下载同签名的 v0.5 APK，交给 Android 系统覆盖安装，切勿卸载。之后使用 App 的检查更新入口。

fork 必须更换 `UpdateConfig.kt` 中自己的更新仓库、包名与资产前缀，并使用自己的签名。不要发布会冒充官方更新源的同名发行包。

## 发布约定

`app/src/main/assets/release-notes.json` 是 App 和 Release 中文更新说明的共同来源。标签如 `v0.5`，发布三个资产：`shicang-v0.5-universal.apk`、`shicang-v0.5-release-notes.json`、`shicang-v0.5-SHA256.txt`。安装包必须经过测试并与源码提交绑定。

发布后匿名重新下载，核对三个资产的大小和 SHA256，再用原生更新模块验证下载及同签名升级。不要覆盖已发布版本；修复使用递增版本。用户本人手机的下载、系统安装及数据保留仍需真实手机验收，CI 和模拟器不能代替。

完整备份保留原 ZIP 格式与原始媒体，尚未加密。0.6 起必须先确认隐私提示才能选择保存位置；确认提示不改变恢复兼容性。

## 仓库更名兼容

当前仓库为 h516df/liuzheba（原 h516df/shicang，同一 GitHub repo ID 1422110562）。包名、证书、SHICANG_* 签名环境变量及历史 shicang-* Release 资产命名不变，避免旧安装包/签名或资产匹配失联。旧客户端不接受 API 仓库重定向，可能需要手动安装一次同签名迁移版本；不能把网页重定向当作旧 App 自动更新已通过。历史验收记录里的旧URL原样保留。

2026-10-20 连接器读取旧 /repos/h516df/shicang/releases/latest 已返回 Moved Permanently，目标为 /repositories/1422110562/releases/latest（连接器未提供HTTP状态码）；另一路匿名云端curl因出口IP限流返回403，这两种结果不可混为一谈。旧手机App实际自动更新路径仍未执行验收。

## 受控云端正式发行

用户授权本轮正式发行后，整合main并等待该精确提交的Cloud development、Android device checks和Secret checks成功。执行：

```sh
gh workflow run production-release.yml --ref main -f expected_sha=<已核实的main完整SHA>
```

流水线分别执行unsigned构建、受保护环境隔离签名、一次性API35模拟器中正式0.16→新版的系统安装与真实备份比较、上传和回读正式Release。签名Secret不注入Gradle。安装包身份检查失败、资料/原件变化或测试未通过均阻止发布。APK、说明和校验和不覆盖已存在正式资产。正式包不添加调试入口。


可先在开发分支验证覆盖升级测试工具，避免在正式签名后才发现旧版UI兼容问题：手动运行`android-device-checks.yml`，同时传入`signed_reference_run`与`signed_reference_sha`。仅接收本仓库main上production-release.yml生成的已有签名候选；验证原证书和固定0.16摘要后，在一次性模拟器运行真实UI覆盖升级。`upgrade-harness-*`附件分别记录当前工具SHA及参考候选SHA；它只证明工具与参考候选的验收，不能当作新源码发布证据。正式发行仍须对精确新main候选重做完整验收。


2026-10-23草稿API修复：未公开草稿可能尚无Git标签，`GET /releases/tags/{tag}`会返回404。发行脚本分页检查含草稿的版本占用，并检查Git标签；通过创建Release的POST响应取得ID，之后按`/releases/{id}`核对身份/资产、公开与回读。既有草稿或标签均阻止重复创建；不覆盖资产。正文从当前版本的`docs/releases/{tag}.md`读取。

0.17发行中首次遇到该404：构建、原签名和真实覆盖升级已通过，三资产也已上传，随后经Release ID421590291逐项核对后公开原草稿，未重传或替换资产。原工作流publish失败记录保留；Release的源码/标签为6826b378061acb117eb08a2515d9070e792f1e3b，后续脚本修复不改变已发布安装包。遇到部分发布失败应先检查现存Release ID、target源码及签名/升级/资产摘要，再接续；不要盲目重跑创建步骤或删除已公开发行。

## 2026-10-31 发布接力与本机 CLI 入口

v0.22 的实际路径：PR [#61](https://github.com/h516df/liuzheba/pull/61) 同源检查全部成功后合入 main `3b4123c4890e7108d884b5f1c64b23b88b3d045b`；该 SHA 的四组 main push 检查全部成功。原有 GitHub 连接器不能直接启动 `workflow_dispatch`，云端浏览器也没有已登录会话；**最后在一台已授权的本机 Windows 设备上复用现有 Desktop Commander 终端及 `gh` 登录，启动既有受保护工作流**。没有复制/输出实际令牌，没有更换签名、GitHub 权限、工作流触发条件或创建另一条发布链。

新加入的 `tools/release/dispatch_verified.py` 是可选的**用户会话预检**。它要求完整且等于当前 main 的 SHA、官方公开仓库、`h516df` 已登录身份、与 tag 一致的发布说明、无占用 Release/草稿/标签及相同源码上未完成的正式发布任务，以及当前 main 精确 SHA 的 Cloud、Android、隔离设备和 Secret 四组 push 成功结果；查询不完整或网络失败则停止。默认只读，19 项合成回归已接入现有 Cloud development Python 检查；**仅在当前任务已有正式发行授权后**才可显式加 `--dispatch`：

```sh
python tools/release/dispatch_verified.py --expected-sha <当前main完整SHA> --tag v0.23
python tools/release/dispatch_verified.py --expected-sha <当前main完整SHA> --tag v0.23 --dispatch
```

新版本获明确发行授权、上述预检合格后，可在**同一已登录的用户终端**一次性启动并等待正式结果：

```sh
python tools/release/dispatch_verified.py --expected-sha <当前main完整SHA> --tag v0.23 --dispatch --wait-seconds 1200
```

这只是调用原有受保护流水线并**只读**核对已完成的 build/sign/upgrade/publish、同源公开 Release、三个资产大小与 GitHub SHA256 及 stable latest；并不会绕开签名/覆盖安装门禁，也不会声称已验证手机。如果派发后网络中断、等待超时或返回状态未知，**不要重复 --dispatch**。先用以下命令只读恢复对原运行的核查；已有失败或多次运行要人工分析原任务和副作用：

```sh
python tools/release/observe_verified.py --expected-sha <原main完整SHA> --tag v0.23 --wait-seconds 1200
```

观察脚本本身不能发布，所有返回的 `phoneUpgrade` 固定为 `not_tested`。默认不等待；无真实匹配运行、同源/作业/附件/摘要证据缺失时拒绝报成功。`--wait-seconds` 最大 1800；此选项不会在 GitHub Actions 内运行。

Windows 使用同一条 `python` 命令；须在具备 `gh` 且已获授权登录的工作环境运行。脚本不替代受保护 `production-release.yml`、`release_gate.py`、原签名隔离和旧版真实升级验证；不允许在 GitHub Actions 中以自带 `GITHUB_TOKEN` 运行。已经发布的版本会因标签占用而拒绝，不能拿以上示例修改或重发 v0.22。用户明确使用本机/DSH时，先发现现有可用终端和身份，不预设浏览器已登录，也不因某个连接器缺少 dispatch 就提前交还用户；没有可用权限时明确停下。

发布后仍由负责人读回完整 Actions 结论、公开正式 Release 的准确源码、非草稿/非预发行、三个原生附件的大小和 GitHub 摘要及 latest；最后才给 APK 下载链接。**v0.22 已发布**：[Run #41178310812](https://github.com/h516df/liuzheba/actions/runs/41178310812) 的 build、sign、upgrade、publish 均 success；[正式 Release](https://github.com/h516df/liuzheba/releases/tag/v0.22) 为非草稿稳定版，APK `shicang-v0.22-universal.apk` 大小 105720492 字节、SHA256 `9a6342ba36d944b5c31e4f1bc4bbd02bf17282aa47c985933aa62d3730d0c327`。模拟器原签名旧版覆盖升级通过，不代表用户 v0.20 手机实际安装与私人资料检查已完成。
