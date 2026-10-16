# 拾藏 · Shicang

Android 本地私人档案应用。人物与物品共用一种结构：只有称呼也能建档，照片、视频和自定义资料以后再补。

## 功能

- **发现**：完整照片或姓名卡；左右浏览同一档案的媒体，上下切换档案。
- **我的**：单一档案库，列表与封面可切换；搜索名称、分类、标签和字段内容。
- **资料与编辑**：完整页面、空白字段、删除撤销、只保存结构的字段模板和安全网页链接。
- **媒体**：导入后保存原件副本；视频手动播放、默认静音，离开或进入后台停止。
- **本地管理**：明暗主题、皮肤、草稿、回收站、完整备份恢复、应用锁和检查更新入口。

不提供云同步、自动抓图、人脸识别或资料上传服务。系统选择器导入的个人资料由使用者自行管理。

## 从源码构建

需要 JDK 21、Android SDK 36 和 SDK Build Tools 36.0.0。安装 Android Studio 后配置本地 `local.properties` 或 `ANDROID_HOME`，接受 Android SDK 许可。

```sh
./gradlew testDebugUnitTest assembleDebug
```

Windows 使用 `gradlew.bat`。调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`，构建不需要正式签名或 GitHub 令牌。依赖版本固定在 Gradle 文件中，首次构建需要联网下载依赖。

正式包自行签名，参考 [发布与签名](docs/RELEASING.md)。自行编译的调试包使用不同签名，不能直接覆盖官方正式包；不要为安装调试包卸载存有资料的正式版。

## 测试

19 项 JVM 逻辑测试覆盖档案编解码、搜索、随机浏览、模板、链接与旧备份兼容。原生测试覆盖持久化、备份、编辑/保存/重开、照片、视频及应用锁。

```sh
./gradlew --init-script tools/verification.init.gradle assembleDebug assembleDebugAndroidTest
```

以上配置给调试包附加 `.verification` 后缀，原生 UI 用例仅允许在这个独立包中运行。使用专用模拟器安装两个调试 APK 后执行：

```sh
adb -s <your-emulator> shell am instrument -w dev.h516.shicang.verification.test/androidx.test.runner.AndroidJUnitRunner
```

人像测试图片已替换为可重现的几何色块；测试视频为无真实人物的合成测试内容。正式新安装不含示例人物、测试照片或测试视频。

## 数据与限制

最低 Android 7.0 / API24。SQLite schema1，备份 format3 支持照片和视频，读取旧格式1/2。备份包含私人资料且未加密，请自行选择安全的保存位置；应用锁不等于备份加密。

这份源码以 v0.4 功能为基准。模拟器和自动测试不能证明所有手机、大尺寸媒体或系统关联跳转均兼容。当前更新模块保留原发行配置；fork 后应配置自己的更新地址和公开 client id，详见发布文档。

## 许可与贡献

项目源码使用 [Apache License 2.0](LICENSE)，第三方组件继续遵守各自许可，见 [第三方声明](THIRD_PARTY_NOTICES.md)。该许可允许修改和商业使用，分发时应保留适用的版权、许可与修改声明；不授予项目名称或图标的商标权。

欢迎提交聚焦的问题和 Pull Request。请用虚构资料复现，不提交私人照片、真实住址、备份包、账号令牌或签名文件。安全问题见 [SECURITY.md](SECURITY.md)。
