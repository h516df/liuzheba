package dev.h516.shicang

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Real Compose rendering with generated, non-personal fixtures. This is not device acceptance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@Config(sdk=[35], application=Application::class, qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArchiveUiSnapshotTest {
    // Each Robolectric sandbox owns a fresh Android looper; never reuse a previous test's Main handler.
    @org.junit.Before fun bindMain() {Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher().immediate)}
    @org.junit.After fun resetMain() {Dispatchers.resetMain()}
    @get:Rule val compose=createComposeRule()
    @Test fun libraryLight()=render("library-light")
    @Test fun libraryDark()=render("library-dark",dark=true)
    @Test fun detailLight()=render("detail-light",detail=true)
    @Test fun settingsLight()=render("settings-light",settings=true)
    @Test fun emptyLight()=render("empty-light",empty=true)
    @Test @Config(qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
    fun narrowLargeDark()=render("library-320-dark-font150",dark=true,fontScale=1.5f)
    @Test @Config(qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
    fun narrowMaximumText()=render("library-320-font200",fontScale=2f)
    @Test fun searchAndLayoutSurviveDetailReturn()=render("search-return",verify={
        compose.onNode(hasSetTextAction()).performTextInput("一封")
        compose.waitUntil(10000){compose.onAllNodesWithText("1 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("列表").performClick()
        compose.onNodeWithText("一封旧信").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("封面").assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("一封")
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("4 份档案").fetchSemanticsNodes().isNotEmpty()}
    })
    @Test fun appliedCategoryCanBeRemoved()=render("filtered",verify={
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onAllNodesWithText("日常收藏").onLast().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("3 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("移除分类筛选").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("4 份档案").fetchSemanticsNodes().isNotEmpty()}
    })
    @Test fun deletingActiveCategoryWhileAwayRestoresLibrary()=render("deleted-filter-return",verify={model->
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onAllNodesWithText("日常收藏").onLast().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("3 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("设置").performClick()
        val category=model.state.value.library.labels.first{it.kind=="category"}
        compose.runOnIdle{model.deleteLabel(category)}
        compose.waitUntil(10000){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            model.state.value.library.labels.none{it.id==category.id}
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("4 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("移除分类筛选").assertDoesNotExist()
    })
    @Test fun emptySearchStillCreatesFirstArchive()=render("empty-search-new",empty=true,verify={
        compose.onNode(hasSetTextAction()).performTextInput("未找到")
        compose.waitForIdle()
        compose.onNodeWithText("新建第一份档案").performClick()
        compose.onNodeWithText("编辑档案").assertExists()
    })
    @Test fun localAiOptInCanCancelWithoutDownloading()=render("local-ai-opt-in",verify={model->
        compose.onNodeWithText("按意思找 · AI").performClick()
        compose.onNodeWithText("下载 / 校验本机 AI 模型").performClick()
        compose.onNodeWithText("下载本机中文语义模型？").assertExists()
        compose.onNodeWithText("暂不下载").performClick()
        org.junit.Assert.assertFalse(model.state.value.modelDownloading)
        org.junit.Assert.assertFalse(File(RuntimeEnvironment.getApplication().filesDir,"local-text-model-bge-v1/model.onnx").exists())
        compose.onNodeWithText("本机 AI 未下载 · 当前仅文字与别名搜索").assertExists()
    })
    @Test fun cancelledRuleDraftDoesNotRewriteLibrary()=render("search-rules-cancel",verify={model->
        val before=model.state.value.library
        compose.onNodeWithText("定制规则").performClick()
        compose.onNodeWithText("我习惯搜的词").performTextInput("旧回忆")
        compose.onNodeWithText("对应的原名称或关键词").performTextInput("一封旧信")
        compose.onNodeWithText("添加 / 更新别名").performClick()
        compose.onNodeWithText("取消").performClick()
        org.junit.Assert.assertEquals(before,model.state.value.library)
    })
    @Test fun rulesBackDiscardsDraft()=render("search-rules-back",verify={model->
        val before=model.state.value.library
        compose.onNodeWithText("定制规则").performClick()
        compose.onNodeWithText("我习惯搜的词").performTextInput("不保存")
        compose.onNodeWithContentDescription("返回").performClick()
        org.junit.Assert.assertEquals(before,runBlocking{model.store.load()})
        compose.onNodeWithText("定制规则").performClick()
        compose.onNodeWithText("我习惯搜的词").assert(hasText("不保存",substring=true).not())
        compose.onNodeWithText("我习惯搜的词").performTextInput("系统返回也不保存")
        compose.onNodeWithText("对应的原名称或关键词").performTextInput("一封旧信")
        compose.onNodeWithText("添加 / 更新别名").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("定制规则").assertExists()
        org.junit.Assert.assertEquals(before,runBlocking{model.store.load()})
    })
    @Test @Config(qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
    fun narrowRulesSaveOnlyOnExplicitAction()=render("rules-320-font200",fontScale=2f,verify={model->
        val before=model.state.value.library
        compose.onNodeWithText("定制规则").performClick()
        compose.onNodeWithText("我习惯搜的词").performScrollTo().performTextInput("旧回忆")
        compose.onNodeWithText("对应的原名称或关键词").performScrollTo().performTextInput("一封旧信")
        compose.onNodeWithText("添加 / 更新别名").performScrollTo().performClick()
        org.junit.Assert.assertEquals(before,runBlocking{model.store.load()})
        val output=File("build/outputs/archive-ui/rules-editor-320-font200.png");output.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
        compose.onNodeWithText("保存规则").assertIsDisplayed().performClick()
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!model.state.value.busy}
        org.junit.Assert.assertEquals(listOf(SearchAlias("旧回忆","一封旧信")),runBlocking{model.store.load()}.searchPreferences.aliases)
        org.junit.Assert.assertEquals(before.archives,runBlocking{model.store.load()}.archives)
    })
    @Test fun aliasSearchShowsReasonAndDoesNotRenameEntry()=render("alias-explanation",verify={model->
        compose.runOnIdle{model.searchPreferences(SearchPreferences(aliases=listOf(SearchAlias("旧回忆","一封旧信"))))}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!model.state.value.busy}
        compose.onNodeWithContentDescription("搜索档案").performTextInput("旧回忆")
        compose.waitUntil(10000){compose.onAllNodesWithText("1 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("一封旧信").assertExists()
        compose.onNode(hasText("自定义别名",substring=true)).assertExists()
    })
    private fun render(name:String,dark:Boolean=false,detail:Boolean=false,settings:Boolean=false,empty:Boolean=false,fontScale:Float=1f,verify:(AppModel)->Unit={}) {
        RuntimeEnvironment.setFontScale(fontScale)
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device",0).edit().putString("generation",uid()).putBoolean("libraryCovers",true).apply()
        val store=Store(app)
        runBlocking {
            store.appearance(Appearance(theme=if(dark) "曜石" else "月白"))
            if(!empty) {
                val category=store.label("category","日常收藏")
                val tag=store.label("tag","值得记住")
                val file=File(app.cacheDir,"generated-landscape.png")
                val bitmap=java.awt.image.BufferedImage(640,760,java.awt.image.BufferedImage.TYPE_INT_RGB)
                val canvas=bitmap.createGraphics()
                canvas.color=java.awt.Color(0xcbd7d3);canvas.fillRect(0,0,640,760)
                canvas.color=java.awt.Color(0xeee5ce);canvas.fillOval(385,105,150,150)
                canvas.color=java.awt.Color(0x6e8980);canvas.fillOval(-160,330,910,670)
                canvas.color=java.awt.Color(0x3e5e57);canvas.fillOval(180,490,820,610);canvas.dispose()
                javax.imageio.ImageIO.write(bitmap,"png",file)
                val id=uid();val target=File(store.photoDir,"$id.original");file.copyTo(target)
                val hash=java.security.MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString(""){"%02x".format(it)}
                val photos=listOf(Photo(id=id,file=target.name,bytes=target.length(),sha256=hash,mime="image/png"))
                store.save(Archive(name="山间的一个下午",category=category,tags=listOf(tag),photos=photos,fields=listOf(Field(name="记忆",value="沿着山路慢慢走，天色刚刚好。")),updated=400))
                store.save(Archive(name="一封旧信",category=category,fields=listOf(Field(name="备注",value="那些被认真写下的话。")),updated=300))
                store.save(Archive(name="周末散步",category=category,photos=photos,updated=200))
                store.save(Archive(name="手写食谱",fields=listOf(Field(name="来源",value="合成测试资料")),updated=100))
            }
        }
        val model=AppModel(app)
        val models=androidx.lifecycle.ViewModelStore().apply{put("fixture",model)}
        try {
        compose.setContent{ShicangApp(model,true,{}, {},{}, {},{}, {})}
        compose.waitUntil(10000){
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            check(model.state.value.message.isEmpty()){model.state.value.message}
            model.state.value.ready
        }
        compose.waitForIdle()
        if(detail){compose.onNodeWithText("山间的一个下午").performClick();compose.waitForIdle()}
        if(settings){compose.onNodeWithContentDescription("设置").performClick();compose.waitForIdle()}
        verify(model);compose.waitForIdle()
        val output=File("build/outputs/archive-ui/$name.png");output.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
        } finally {models.clear();compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.resourcesClosed};runBlocking{store.close()}}
    }
}
