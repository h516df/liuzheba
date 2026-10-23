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
