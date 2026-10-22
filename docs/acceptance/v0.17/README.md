# v0.17 云端候选与覆盖安装准备

本批回应用户“做”，在已推送的收录/发现页/导入保护实现上准备下一版。发布准备在独立分支`codex/release/v0.17-cloud-20261022` / [PR #33](https://github.com/h516df/liuzheba/pull/33)接续；明确包含PR #31的最终提交`9993abeba616b22764968935edc966114b5f206d`，不是另外重做主线或自动合并其他PR。

## 版本与产物

- 当前候选源：`df9bc2ba5ebe6b57ecf3f44b48956ab8bc5720f5`；根树`811c4db884ccac28cd32a7d48ffbad472a8084b8`，app树`850cc7a4ec5739ea6fd8f8a94347e54ea0fdcc0d`。
- 版本0.17/code18；正式包名`dev.h516.shicang`、原证书固定值、资料schema与备份格式保持。更新说明与设置页对应检查已更新。
- 现有Actions规则兼容`codex/release/`协调分支；[push构建](https://github.com/h516df/liuzheba/actions/runs/39755897668)与[PR构建](https://github.com/h516df/liuzheba/actions/runs/39756105135)均成功，已取得unsigned候选附件并核对归档摘要。APK为0.17/code18，105641307字节，SHA256 `a08113ce69c789946f17f8ab4752ba187cd78455ea2bd276edee8009cd147da3`；仅候选，不可作为已签名覆盖更新。附件ID12416360775，归档摘要`eae6dde7f1033d4f4ccef38cd4f6cfd5a0d55bac8ff2f902db9f122bb4bc61fe`。
- 正式v0.16基线已通过公开Release取得资产并核对SHA256：`8fe854b89f4971133fb21209dd599f77a143cbc4a8737264c69f96f9b09c2014`，105647347字节，实际版本0.16/code17。真实证书SHA256与固定值`67127caf5a760d51f11dc5a2fadcf44ddc30ce836fa14ffb9efd96c4b7e95a26`相同。

## 已验证

actionlint 1.7.7（官方归档校验和已比对）对改动工作流语义检查passed；`verify_apk.py`语法检查passed。以真实旧正式APK和既有verification调试APK执行两个拒绝场景：同versionCode被阻止、非原正式证书被阻止；两项检查均按预期退出非零并写blocked回执。这些是校验器负向检查通过，不是候选包已具备升级资格。

真实unsigned候选同样被校验器在签名阶段拒绝，第三个负向检查passed。已下载push构建的XML，核实更新说明读取和设置页说明展示2/2、零失败/错误/跳过。报告归档摘要`57e4090b844c8e3a0159c1dfb70f931ab9d0e0cc9358396352d57ca7162f16b3`。本地候选、XML及拒绝回执位于`/workspace/scratch/liuzheba-v017-candidate`，旧版APK元数据与另外两份拒绝回执位于`/workspace/scratch/liuzheba-release-inputs`；APK不提交源码。

[API35隔离设备检查](https://github.com/h516df/liuzheba/actions/runs/39756105215)success，已下载XML核实6/6、零失败/错误/跳过（归档SHA256 `4eac9e022d8ff6d3f019a10ea58566e613088030c29ec6bbda0fd5e433fda15a`）。合成merge `35aa3732c7291f0b1af34d28dc04f9cf988471f9`根树与候选811c4db完全一致。[Secret检查](https://github.com/h516df/liuzheba/actions/runs/39756105362)success；[完整Cloud检查](https://github.com/h516df/liuzheba/actions/runs/39756105127)已读回completed/success。本轮没有下载完整JVM XML重新计数，精确数量不引用旧实现。

该脚本使用SDK aapt/apksigner核对真实APK，成功只写`metadata_passed`，始终另标安装及保数据验收未运行；不会安装、卸载、清数据或发布。

导入保护受测实现`e021dca`的23/23宿主检查属于继承证据，覆盖原件保留、部分失败重试、新输入不被清掉、取消及数据库关联失败后重开摘要一致；当前版本的最终构建/完整CI以候选对应记录为准，不挪用旧结果。

## 已核实的签名阻塞

已有Make GitHub OAuth管理员连接可访问本仓库；通过其GET `/repos/h516df/liuzheba/actions/secrets`成功读回`total_count=0`。未读取或输出密钥值。仓库environments为0，当前managed cloud无secret/变量绑定；既有Make场景按release/签名名称检索也无匹配，现有工作流只有debug/unsigned构建。旧GH_TOKEN的403是单个连接限制，现已通过合法现有连接核实配置为空，不能继续以403代替结论。

历史正式构建使用旧本机的仓库外私钥；当前已接入渠道不含该私钥。不能从证书公钥或已签APK反推出原私钥，不能用新私钥替代后宣称可覆盖原App。未访问旧电脑、未新建正式签名、未修改官方更新源或已发布资产。

因此当前状态是候选准备，正式签名、原签名旧版→新版覆盖安装/资料保留和实体手机验收尚未完成。unsigned附件不作为用户可安装的正式更新；保留原正式App与原有备份，不要求用户卸载或反复寻找配置位置。
