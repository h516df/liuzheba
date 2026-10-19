# 档案书架：视觉与找回体验

## 目标与边界

以真实档案内容为中心，改善封面/姓名卡、列表、详情、搜索恢复路径以及设置的视觉一致性。人物与物品仍共用档案结构；不改媒体下载、播放器、原件、备份格式、包名或签名。不复制外部项目代码、图标或素材，不新增运行时依赖。

## 已核查的 GitHub 参考

- [Now in Android designsystem](https://github.com/android/nowinandroid/tree/main/core/designsystem) / [search](https://github.com/android/nowinandroid/tree/main/feature/search)：官方 Compose/M3，Apache-2.0。参考主题角色、一致组件、显式搜索状态与可访问性测试；不搬架构。
- [Read You](https://github.com/ReadYouApp/ReadYou)：GPL-3.0，仅参考列表和设置的信息层级，不引入源码或素材。
- [Readest](https://github.com/readest/readest)：AGPL-3.0，仅参考封面库的标题与次级信息层级，不引入源码或素材。
- [Quillpad](https://github.com/quillpad/quillpad)、[Markor](https://github.com/gsantner/markor)、[Notesnook](https://github.com/streetwriters/notesnook)、[Joplin](https://github.com/laurent22/joplin)：对比后未采用其任务/笔记导向导航，避免将私人档案改造成笔记工具。

## 原创实现方向

- 中性纸色与低饱和绿色强调；深色独立成对的 surface/onSurface 角色。
- 20dp 页面边距、8/12/16/24dp 间距阶梯；系统中文字体，避免字体下载与回退割裂。
- 封面和姓名卡同等身份感，标题在图外；有图/无图列表缩略区统一。
- 搜索、选中条件、数量/排序/布局分层；条件可逐项撤销，空库与无结果行为不同。
- 详情把身份、媒体与资料分组；无媒体和无字段是可补充状态。
- 页面操作区可随大字号增高，不通过缩小字体适配。点击目标至少48dp，媒体子入口与档案详情入口保留。

## 验证分类

主机 Robolectric/Roborazzi 渲染的是应用 Compose 代码，使用生成图形与虚构档案。它不替代模拟器或实体手机验收。设备级媒体、签名与覆盖升级不由本轮静态渲染证明。最终报告逐项区分通过、失败与未执行。
