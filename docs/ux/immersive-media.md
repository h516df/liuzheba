# 内容优先的沉浸浏览

- 档案墙的媒体区域直接打开全屏；标题/文字卡仍进入档案详情。
- 全屏保留边缘返回、页数和更多操作，轻触图片收起/显示控件。作者、标签和备注通过“资料”按需展开，不持续盖住媒体。
- 回到原来的筛选和精确滚动位置。全屏期间卸载底层档案墙，避免上下栏消失导致隐藏列表重新布局和末端位置被夹紧。
- 发现页只轮播含照片/视频的档案。无媒体时显示紧凑引导，不用大空白卡替代图片；文字档案仍保留在档案页。
- 复用现有 Telephoto 缩放与 Media3 播放；资料/菜单/外观覆盖层期间停用视频，保留系统返回和无障碍入口。

测试使用合成媒体实际 Compose 原生渲染。设备手势、平台 App 跳转与真实视频旋转/焦点仍单独验收，不由 host 截图代替。

## 参考与取舍

- Fossify Gallery 的轻触切换全屏原则：https://github.com/FossifyOrg/Gallery/blob/main/app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt （GPL-3.0，仅参考交互，没有复制实现。）
- Aves 的查看与资料分离：https://github.com/deckerst/aves （BSD-3-Clause，仅借鉴信息层级。）

未引入其他图库代码、依赖或复杂自定义系统；“我的视图”和模板个性化仍为之后的独立功能。
