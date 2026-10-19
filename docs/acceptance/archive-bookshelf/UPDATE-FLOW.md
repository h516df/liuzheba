# 应用内更新：最小延续方案

复用留册既有 `dev.h516.update`，不引入第三方更新SDK。对比参考 azhon/AppUpdate（Apache-2.0）与 jenly1314/AppUpdater 的更新交互；未复制其源码或素材。

- 默认仍手动检查。首次解锁后的正式版提供一次非强制“开启新版本提醒”；明确选择后，前台最多24小时一次匿名检查。设置可关闭。不发送档案、媒体或账号，不自动下载、不增加后台服务/通知权限。
- 更新会话由AppModel持有，离开设置及旋转保持状态。销毁会话取消网络与线程，过期回调不能回写。
- 私有缓存只存APK；私有偏好只保存已经验证的公开发行元数据。恢复路径必须符合固定发布文件名且位于自身更新缓存目录。
- 下载后、进程恢复时、每次安装前核对大小/SHA256/API digest/包名/新版code/versionName/当前已装签名/ABI。正式渠道还核对公开的原证书SHA256指纹。
- 多APK校验文件按选中的文件名取hash；发行tag与说明版本必须一致。
- 缺少安装来源许可时解释用途，用户自行进入系统设置；返回且已允许后重新校验并打开系统安装确认。拒绝则保留已验证文件，失败可重试，无需卸载旧版。
- `.verification` 和 debug/非正式签名版本禁止正式更新。旧硬编码v0.6的联网验收改为隔离门禁测试；正式成功链须晚间原证书旧→新真实验收，无测试后门。

待CI与独立审查确认。本轮mock不会下载真实安装包；不能把mock测试描述成正式安装或覆盖升级成功。

参考：
- https://github.com/azhon/AppUpdate
- https://github.com/jenly1314/AppUpdater
- https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()
- https://developer.android.com/google/play/app-updates
