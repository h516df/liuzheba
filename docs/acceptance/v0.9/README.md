# 现代播放、左滑回收与分享建档

29 JVM 测试通过；原生长按倍速松手恢复、双击/按钮跳转、拖动进度、后台暂停/续播、左滑取消/回收/恢复、原生 ACTION_SEND 预填及保存重读通过。已有资料/备份/应用锁回归通过；截图为合成资料。正式签名与0.8一致。短链网络只尝试同平台公开重定向，失败保留原文与可编辑结果，不承诺平台登录后资料或头像抓取。

安装包 SHA256：`adf5eea8d1d4c7cb3b61ec595008325611d54b86742c2a66e6f8a252b4eb4b41`。大小 3263496 字节。源码及安装包对应本次分支；GitHub合并后标签绑定完整提交。

用户手机未连接，真实手机网络、本人系统覆盖安装未验收。保留数据和签名；出现问题采用后续递增版本修复，不建议降级写入。

## 参考与许可

播放器沿用 Media3，参考 https://developer.android.com/media/media3/ui/compose-customization 与 https://github.com/anilbeesetti/NextPlayer 的交互方向，未复制GPL源码。分享入口按 https://developer.android.com/develop/ui/compose/sharing/receive?hl=en 实现。
