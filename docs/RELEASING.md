# 构建、签名与更新

## 调试构建

`testDebugUnitTest assembleDebug` 不需要正式密钥。调试签名与官方正式签名不同，不能覆盖正式版。并排测试使用 `tools/verification.init.gradle` 的 `.verification` 独立包；不要为调试卸载存有资料的正式版。

## 正式签名

公开仓库不包含正式密钥、密码或旧私有历史。维护者在仓库外保存密钥，另留离线备份。构建时通过 `SHICANG_SIGNING_PROPERTIES` 指向包含 `storePassword` 和 `keyPassword` 的本地配置，通过 `SHICANG_KEYSTORE` 指向正式 JKS，alias 为 `shicang`。不要打印或提交这些内容。也支持本地忽略的 `keystore.properties` 和 `keystore/shicang.jks`，但官方维护推荐使用仓库外路径。

没有签名配置时，`assembleRelease` 生成 unsigned APK，不能发布为官方安装包。正式更新必须沿用相同签名并递增 versionCode。安装前核对大小、SHA256、包名、版本、签名及架构；异常时保留旧版本，不通过卸载解决签名冲突。

## 公开更新

v0.5 起匿名读取 `h516df/shicang` 的公开稳定 Release，不需要 GitHub 登录，不发送旧版更新令牌。检查更新时清理旧更新授权存储，不读取或解密旧令牌，不影响档案数据。草稿和预发布版本不进入自动更新。

旧版使用私有仓库授权；仓库重建后可能无法继续检查更新。首次迁移应从公开 Release 下载同签名的 v0.5 APK，交给 Android 系统覆盖安装，切勿卸载。之后使用 App 的检查更新入口。

fork 必须更换 `UpdateConfig.kt` 中自己的更新仓库、包名与资产前缀，并使用自己的签名。不要发布会冒充官方更新源的同名发行包。

## 发布约定

`app/src/main/assets/release-notes.json` 是 App 和 Release 中文更新说明的共同来源。标签如 `v0.5`，发布三个资产：`shicang-v0.5-universal.apk`、`shicang-v0.5-release-notes.json`、`shicang-v0.5-SHA256.txt`。安装包必须经过测试并与源码提交绑定。

发布后匿名重新下载，核对三个资产的大小和 SHA256，再用原生更新模块验证下载及同签名升级。不要覆盖已发布版本；修复使用递增版本。用户本人手机的下载、系统安装及数据保留仍需真实手机验收，CI 和模拟器不能代替。
