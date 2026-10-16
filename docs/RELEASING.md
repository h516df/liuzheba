# 构建、签名与更新

## 调试构建

`testDebugUnitTest assembleDebug` 不需要正式密钥。调试签名与官方正式签名不同，不能覆盖安装正式版。若要并排测试，使用 `tools/verification.init.gradle` 的 `.verification` 独立包。

## 正式签名

仓库不包含正式密钥或密码。维护者在本机或受控密钥存储中保管密钥，并至少保留独立离线备份；不要把密钥放进 Issue、构建日志、Release 或 Git 历史。

现有 Gradle 配置可从本地未跟踪的 `keystore.properties` 读取 `storePassword` 和 `keyPassword`，正式密钥位置为本地忽略目录 `keystore/shicang.jks`，alias为 `shicang`。这些文件只能在受控构建环境创建，不应提交。没有这些文件时，调试构建仍可运行，release 不会使用官方签名。

更新必须沿用相同签名并递增 versionCode。安装前核对大小、SHA256、包名、版本、签名及架构；异常时保留旧版本。不要通过卸载来处理签名冲突，因为卸载会清除应用专有目录内的资料。

## 更新模块

当前源码来自 v0.4，保留发行配置与设备授权模块。`UpdateConfig.kt` 中的 client id 是公开标识，不是 client secret；库中不提供服务器秘密或维护者账号令牌。

fork 后需要配置自己的仓库、仓库标识和公开 client id，并检查 `UpdateHttp.kt` 的地址白名单。源码公开不代表旧发行库已经迁移；不得在未核对真实地址和安装行为前声称所有用户的自动更新可用。

若清理历史时更换 GitHub 仓库，先准备旧版本到新入口的迁移、同签名候选和回退，再处理旧库。删除仓库会影响旧 Release、Issue/PR 和现有更新入口，不能靠修改 README 修复。

## 发布约定

App 的 `app/src/main/assets/release-notes.json` 与 Release 中文更新说明使用同一份来源。标签例如 `v0.4`，资产为 `shicang-v0.4-universal.apk`、`shicang-v0.4-release-notes.json`、`shicang-v0.4-SHA256.txt`。

发布后重新下载核对资产大小和摘要。CI 或模拟器通过不能替代用户本人在手机上完成下载与系统安装。
