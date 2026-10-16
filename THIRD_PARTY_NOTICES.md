# 第三方声明

本项目不把第三方库重新许可为自己的代码。Gradle 按固定版本从 Google Maven、Maven Central 和 Gradle Plugin Portal 获取依赖；使用者分发二进制时仍应保留适用的第三方声明。

| 组件 | 当前主要版本 | 许可 / 来源 |
|---|---|---|
| AndroidX Core、Activity、Lifecycle、Fragment、Biometric、ExifInterface、Compose / Material3 | 见 `app/build.gradle.kts` | Apache-2.0；[AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| AndroidX Media3 ExoPlayer / UI | 1.11.1 | Apache-2.0；[官方许可](https://github.com/androidx/media/blob/release/LICENSE) |
| Coil | 3.4.0 | Apache-2.0；[官方许可](https://github.com/coil-kt/coil/blob/main/LICENSE.txt) |
| Telephoto | 0.19.0 | Apache-2.0；[官方仓库](https://github.com/saket/telephoto)，固定版本 Maven POM 声明 |
| Kotlin 与协程 | 见 Gradle 解析版本 | Apache-2.0；[Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)、[协程](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| Gradle Wrapper / Gradle | Wrapper 随源码，分发版9.1.0 | Apache-2.0；[Gradle](https://github.com/gradle/gradle/blob/master/LICENSE)；其分发包保留自身完整第三方许可 |
| JUnit | 4.13.2，仅测试 | EPL-1.0；[JUnit4](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt) |
| JSON-java | 20250517，仅 JVM 测试 | Public Domain；[官方许可](https://github.com/stleary/JSON-java/blob/master/LICENSE) |

`app/src/main/java/dev/h516/update` 是项目维护者自己的更新模块，不包含 GitHub client secret、PAT 或用户令牌；公开 client id 是客户端标识，不是秘密。第三方使用自己的发行配置时，应使用自己的授权应用。

`app/src/androidTest/assets/v03-demo.png` 为几何测试数据，生成方法见 `tools/generate_test_image.py`。`sample-video.mp4` 为项目的合成视频测试夹具，不含真实用户媒体；二者只进入测试 APK。

没有随本开源候选分发旧设计预览、旧截图或来源不明的原型图片。Android SDK 由使用者单独安装并接受其许可；它不是本源码许可的一部分。
