package dev.h516.shicang

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

/** Native Compose rendering; public network success and installed platform Apps are not simulated. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthorWorkflowSnapshotTest {
    @get:Rule val compose=createComposeRule()
    @Before fun bindMain(){Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher().immediate)}
    @After fun resetMain(){Dispatchers.resetMain()}
    @Test fun previewEditConfirmAssociateAndReturn()=render("author-light") { model,archive ->
        compose.runOnIdle{model.openAuthors(archive.id);model.previewAuthor(fixtureAuthor())}
        compose.onNodeWithText("核对作者资料").assertExists()
        compose.onNodeWithText("公开昵称（未获取可留空）").performTextReplacement("核对后的昵称")
        shot("author-preview")
        compose.onNodeWithText("保存并关联此档案").performClick()
        wait{!model.state.value.busy && model.state.value.authorPreview==null}
        val library=runBlocking{model.store.load()}
        assertEquals("核对后的昵称",library.authors.single().identities.single().nickname)
        assertEquals(listOf(library.authors.single().id),library.active.single().authorIds)
        compose.onNodeWithText("打开微博主页").assertExists()
        shot("author-saved-card")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText(archive.name).performClick()
        compose.onNodeWithText("打开微博主页").performScrollTo().assertExists()
    }
    @Test fun cancelledPreviewNeverCreatesAuthorOrAssociation()=render("author-cancel") {model,archive->
        compose.runOnIdle{model.openAuthors(archive.id);model.previewAuthor(fixtureAuthor())}
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("核对作者资料").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        assertTrue(runBlocking{model.store.load()}.authors.isEmpty())
        assertTrue(runBlocking{model.store.load()}.active.single().authorIds.isEmpty())
    }
    @Test fun editingExistingAuthorDoesNotSilentlyAssociateIt()=render("author-edit-no-link") {model,archive->
        val existing=fixtureAuthor()
        runBlocking{model.store.saveAuthor(existing)}
        compose.runOnIdle{model.openAuthors(archive.id);model.previewAuthor(existing)}
        // Refresh model's library before editing an existing card, as normal navigation does.
        compose.runOnIdle{model.appearance(model.state.value.appearance)}
        wait{!model.state.value.busy && model.state.value.library.authors.any{it.id==existing.id}}
        compose.onNodeWithText("公开昵称（未获取可留空）").performTextReplacement("只修改昵称")
        compose.onNodeWithText("保存作者资料").performClick()
        wait{!model.state.value.busy && model.state.value.authorPreview==null}
        assertTrue(runBlocking{model.store.load()}.active.single().authorIds.isEmpty())
        assertEquals("只修改昵称",runBlocking{model.store.load()}.authors.single().identities.single().nickname)
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
    fun largeDarkPreviewCanSaveVerifiedLinkWithMissingNames()=render("author-dark-font200",dark=true,font=2f) {model,archive->
        compose.runOnIdle{model.openAuthors(archive.id);model.previewAuthor(Author(identities=listOf(AuthorProfiles.parse("https://weibo.com/u/123456789")!!)))}
        shot("author-dark-large-preview")
        compose.onNodeWithText("保存并关联此档案").assertIsDisplayed().performClick()
        wait{!model.state.value.busy && model.state.value.authorPreview==null}
        assertEquals("",runBlocking{model.store.load()}.authors.single().name)
        compose.onNodeWithText("未填写称呼的作者").performScrollTo().assertExists()
    }
    @Test fun unresolvedShareStaysEditableAndOpensOriginalHttpsWithoutCreatingAuthor()=render("author-unresolved-share") {model,archive->
        val source="https://xhslink.cn/o/SyntheticFixture"
        val text="@合成作者 分享主页 $source"
        compose.runOnIdle{model.openAuthors(archive.id,text);model.message("已识别小红书分享链接，但未取得作者主页")}
        compose.onNodeWithText("粘贴作者主页分享链接").assertTextContains(text)
        compose.onNodeWithText("打开原链接").performScrollTo().performClick()
        val intent=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_VIEW,intent.action)
        assertEquals(source,intent.dataString)
        assertTrue(intent.hasCategory(android.content.Intent.CATEGORY_BROWSABLE))
        assertNull(model.state.value.authorPreview)
        assertTrue(runBlocking{model.store.load()}.authors.isEmpty())
        compose.onNodeWithText("粘贴作者主页分享链接").assertTextContains(text)
    }
    private fun fixtureAuthor()=Author(name="合成作者",identities=listOf(AuthorProfiles.parse("https://weibo.com/u/123456789")!!.copy(nickname="候选昵称",publicAccount="测试公开账号")))
    private fun wait(predicate:()->Boolean){compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();predicate()}}
    private fun shot(name:String){compose.waitForIdle();val file=File("build/outputs/author-workflow/$name.png");file.parentFile!!.mkdirs();compose.onRoot().captureRoboImage(file.absolutePath)}
    private fun render(name:String,dark:Boolean=false,font:Float=1f,verify:(AppModel,Archive)->Unit) {
        RuntimeEnvironment.setFontScale(font)
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val store=Store(app);val archive=Archive(name="合成收藏",fields=listOf(Field(name="备注",value="只有测试资料")))
        runBlocking{store.save(archive);store.appearance(Appearance(theme=if(dark)"曜石" else "月白"))}
        val model=AppModel(app);val owners=androidx.lifecycle.ViewModelStore().apply{put("fixture",model)}
        try{
            compose.setContent{ShicangApp(model,true,{}, {},{}, {},{}, {})}
            wait{model.state.value.ready};verify(model,archive);shot(name)
        }finally{owners.clear();wait{model.resourcesClosed};runBlocking{store.close()}}
    }
}
