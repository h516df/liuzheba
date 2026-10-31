package dev.h516.shicang

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

/** Real application entry points, synthetic files only. No Android codec or SAF-provider claim. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w320dp-h900dp-xhdpi",shadows=[VideoEditStoreTest.Metadata::class])
@OptIn(ExperimentalCoroutinesApi::class,coil3.annotation.DelicateCoilApi::class)
abstract class UnifiedNavigationFixture {
    @get:Rule val compose=createComposeRule()
    @Before fun bind(){Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher().immediate)}
    @After fun reset(){Dispatchers.resetMain();RuntimeEnvironment.setFontScale(1f)}
    protected fun idle(check:()->Boolean){compose.waitUntil(15000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();check()}}
    protected fun screenshot(name:String){val file=File("build/outputs/unified-navigation/$name.png");file.parentFile!!.mkdirs();compose.onAllNodes(isRoot()).onLast().captureRoboImage(file.absolutePath)}
    protected fun fixture(video:Boolean=false,font:Float=1f,choose:(String)->Unit={},take:(String)->Unit={},check:(AppModel,Archive)->Unit) {
        RuntimeEnvironment.setFontScale(font)
        val app=RuntimeEnvironment.getApplication()
        android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888).apply{copyPixelsToBuffer(java.nio.ByteBuffer.allocate(4));recycle()}
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).putBoolean("frameHintV2",true).commit()
        val store=Store(app)
        val photos=(1..3).map{n->
            val file=File(store.photoDir,"${uid()}.original")
            if(video)file.writeText("synthetic video $n") else {
                val bitmap=java.awt.image.BufferedImage(240,360,java.awt.image.BufferedImage.TYPE_INT_RGB)
                bitmap.createGraphics().apply{color=java.awt.Color(150+20*n,190,190);fillRect(0,0,240,360);color=java.awt.Color(250,220,160);fillOval(140,45,55,55);color=java.awt.Color(65+15*n,120,105);fillOval(-70,180,300,260);color=java.awt.Color(40,85+15*n,80);fillOval(80,230,270,250);dispose()}
                javax.imageio.ImageIO.write(bitmap,"png",file)
            }
            Photo(id=file.name.removeSuffix(".original"),file=file.name,bytes=file.length(),sha256=sha(file),mime=if(video)"video/mp4" else "image/png",duration=1200)
        }
        val author=Author(name="合成作者")
        val a=Archive(name="合成留存档案",photos=photos,cover=photos.first().id,authorIds=listOf(author.id),fields=listOf(Field(name="旧备注",value="不改变")))
        runBlocking{store.saveAuthor(author);store.save(a);store.close()}
        val loader=coil3.ImageLoader.Builder(app).components{add(coil3.decode.BitmapFactoryDecoder.Factory())}.build();coil3.SingletonImageLoader.setUnsafe(loader)
        val model=AppModel(app);val owners=androidx.lifecycle.ViewModelStore().apply{put("fixture",model)}
        try {
            compose.setContent{ShicangApp(model,true,{},{},choose,take,{},{})};idle{kotlin.check(model.state.value.message.isEmpty()){model.state.value.message};model.state.value.ready}
            // AppModel.ready can precede ArchiveLibraryScreen's asynchronous
            // LaunchedEffect search. Every fixture seeds one real archive, so
            // wait for its visible card before a test attempts its first touch.
            // An absent card still fails after the bounded 15-second timeout.
            idle{compose.onAllNodesWithText(a.name,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            check(model,a)
        } finally {owners.clear();idle{model.resourcesClosed};loader.shutdown()}
    }
    protected fun mediaNumber(total:Int):Int {
        idle{(1..total).any{compose.onAllNodesWithText("$it / $total").fetchSemanticsNodes().isNotEmpty()}}
        return (1..total).first{compose.onAllNodesWithText("$it / $total").fetchSemanticsNodes().isNotEmpty()}
    }
}

class UnifiedDiscoveryNoteUiTest:UnifiedNavigationFixture() {
    @Test fun discoveryNoteDraftSaveAndReturnKeepsOriginalMedia()=fixture {model,a->
        compose.onNodeWithTag("nav-frame").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-library").assertIsDisplayed();compose.onNodeWithContentDescription("更多").assertIsDisplayed()
        compose.onNodeWithContentDescription("打开档案：${a.name}").performClick()
        compose.onNodeWithText("档案详情").assertExists();compose.onNodeWithTag("nav-frame").assertDoesNotExist()
        compose.onNodeWithText("编辑").performClick();idle{model.state.value.editor!=null}
        compose.onNodeWithTag("archive-body").performTextInput("合成记忆：不丢草稿")
        compose.onNodeWithContentDescription("返回").performClick();idle{model.state.value.editor==null}
        assertEquals("合成记忆：不丢草稿",runBlocking{model.store.load()}.noteDrafts.single().body)
        compose.onNodeWithText("编辑").performClick();idle{model.state.value.editor!=null}
        compose.onNodeWithTag("archive-body").assertTextContains("不丢草稿",substring=true)
        compose.onNodeWithText("保存").performClick();idle{model.state.value.editor==null && model.state.value.library.notes.size==1}
        compose.onNodeWithTag("archive-text").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("archive-text").assertTextContains("不丢草稿",substring=true)
        assertNull(model.noteEditor.state.value)
        screenshot("archive-note-reader")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText(a.name).assertExists();compose.onNodeWithTag("nav-frame").assertIsSelected()
        compose.onNodeWithContentDescription("打开档案：${a.name}").assertExists()
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithTag("nav-settings").performClick();compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-frame").performClick()
        val saved=runBlocking{model.store.load()};assertEquals(a.authorIds,saved.active.single().authorIds);assertEquals(a.photos,saved.active.single().photos)
        screenshot("discovery-return")
    }
}

class UnifiedLibraryReturnUiTest:UnifiedNavigationFixture() {
    @Test fun filterAndArchiveEditorCancellationStayInContext()=fixture {model,_->
        compose.onNodeWithContentDescription("搜索档案").performTextInput("合成")
        compose.onNodeWithTag("nav-settings").performClick();compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("搜索档案").assertTextContains("合成")
        compose.onNodeWithText("合成留存档案").performClick()
        compose.onNodeWithText("编辑",useUnmergedTree=true).performClick();idle{model.state.value.editor!=null}
        // The existing unchanged editor is cancelled through the real Back control.
        compose.onNodeWithContentDescription("返回").performClick();idle{model.state.value.editor==null}
        compose.onNodeWithText("档案详情").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("搜索档案").assertTextContains("合成")
    }
}

class UnifiedBackupLargeTextUiTest:UnifiedNavigationFixture() {
    @Test fun singleBackupDestinationAtTwoHundredPercent()=fixture(font=2f) {_,_->
        compose.onNodeWithTag("nav-settings").performClick();compose.onNodeWithText("设置").assertExists()
        screenshot("mine-font200")
        compose.onNodeWithText("资料与备份").assertDoesNotExist()
        compose.onNodeWithText("自动保护 · 未开启").assertDoesNotExist()
        compose.onNodeWithText("备份与恢复").performScrollTo().performClick()
        compose.onNodeWithTag("backup-screen").assertExists()
        screenshot("backup-top-font200")
        compose.onNodeWithText("开启自动保护").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(PROTECTION_SETUP_NOTICE).assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("导出备份文件（换机或留存）").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        compose.onNodeWithText("恢复已有资料").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("选择原保护目录").performClick()
        compose.onNodeWithText(PROTECTION_RECOVERY_NOTICE).assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("撤销最近一次恢复").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("暂无可撤销的恢复").assertIsDisplayed()
        screenshot("backup-font200")
        compose.onNodeWithContentDescription("返回").performClick();compose.onNodeWithText("设置").assertExists()
    }
}

class UnifiedVideoReturnUiTest:UnifiedNavigationFixture() {
    @Test fun editorEntryCancelAndSyntheticSaveBoundaryKeepTheViewedOriginal()=fixture(video=true) {model,a->
        compose.onNodeWithTag("nav-frame").performClick();val first=mediaNumber(3)
        compose.onNodeWithContentDescription("更多").performClick();compose.onNodeWithText("编辑视频").performClick()
        idle{compose.onAllNodesWithTag("video-region").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("返回").performScrollTo().performClick()
        assertEquals(first,mediaNumber(3))
        // Exercise the actual AppModel commit boundary with synthetic exported bytes. This is NOT a Transformer/codec result.
        val original=a.photos[first-1];val source=model.photoFile(original)
        val output=File(model.getApplication<Application>().cacheDir,"synthetic-edited.mp4").apply{writeText("synthetic edited bytes")}
        runBlocking{model.saveEditedVideo(a,original,source,output)};output.delete()
        idle{model.state.value.library.active.single().photos.size==4}
        assertEquals(first,mediaNumber(4))
        assertEquals(a.photos,model.state.value.library.active.single().photos.take(3))
        assertEquals(a.authorIds,model.state.value.library.active.single().authorIds)
    }
}

/** Injected IME geometry checks Compose inset handling, not a device keyboard implementation. */
class UnifiedKeyboardUiTest:UnifiedNavigationFixture() {
    @Test fun noteSaveRemainsReachableWithLargeTextAndInjectedIme()=fixture(font=2f) {model,_->
        compose.onNodeWithText("合成留存档案").performClick()
        compose.onNodeWithText("编辑").performClick();idle{model.state.value.editor!=null}
        compose.onNodeWithTag("archive-body").performScrollTo().performTextInput("合成输入法检查")
        compose.runOnIdle {
            android.view.inspector.WindowInspector.getGlobalWindowViews().last().dispatchApplyWindowInsets(android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.ime(),android.graphics.Insets.of(0,0,0,600))
                .setVisible(android.view.WindowInsets.Type.ime(),true).build())
        }
        compose.onNodeWithText("保存").assertIsDisplayed()
        screenshot("note-font200-injected-ime")
        compose.onNodeWithText("保存").performClick();idle{model.state.value.editor==null && model.state.value.library.notes.isNotEmpty()}
        assertEquals("合成输入法检查",model.state.value.library.notes.single().body)
    }
}

class UnifiedCreateUiTest:UnifiedNavigationFixture() {
    @Test fun newArchiveShowsDistinctCreationAndOpensOnlyAfterSave()=fixture {model,_->
        compose.onNodeWithContentDescription("添加内容").performClick()
        compose.onNodeWithText("新建档案").performClick()
        compose.onNodeWithText("新建档案").assertExists()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("新建合成档案")
        compose.onNodeWithText("保存").performClick()
        // Store reload precedes work's busy=false; navigation intentionally waits for completion.
        idle{!model.state.value.busy && model.state.value.editor==null && model.state.value.library.active.any{it.name=="新建合成档案"}}
        idle{compose.onAllNodesWithText("档案详情").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("档案详情").assertExists()
        compose.onNodeWithText("新建合成档案").assertExists()
    }
}

class UnifiedBackupRegularUiTest:UnifiedNavigationFixture() {
    @Test fun dailyBackupScreenSeparatesSetupExportAndRecovery()=fixture {_,_->
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("备份与恢复").performScrollTo().performClick()
        compose.onNodeWithText("开启自动保护").assertIsDisplayed()
        compose.onNodeWithText("导出备份文件（换机或留存）").assertIsDisplayed()
        compose.onNodeWithText("恢复已有资料").assertIsDisplayed()
        screenshot("backup-regular")
        compose.onNodeWithText("保护范围与注意事项").performClick()
        compose.onNode(hasText("同一手机损坏或丢失",substring=true)).assertExists()
        compose.onNodeWithText("收起保护说明").performClick()
        compose.onNodeWithText("恢复已有资料").performClick()
        compose.onNodeWithText("选择备份文件").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("开启自动保护").assertExists()
    }
}

class UnifiedProtectionIntentUiTest:UnifiedNavigationFixture() {
    @Test fun recoveryFromEmptyFolderNeverEnablesProtectionOrWritesFiles()=fixture {model,_->
        val app=RuntimeEnvironment.getApplication()
        val directory=File(app.cacheDir,"empty-recovery-${uid()}").apply{mkdirs()}
        val provider=ProtectionStoreTest.LocalDocuments(directory)
        provider.attachInfo(app,android.content.pm.ProviderInfo().apply{authority="com.android.externalstorage.documents";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("com.android.externalstorage.documents",provider)
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents","primary:Documents")
        val before=runBlocking{model.store.load()}
        assertTrue(model.prepareProtectionSelection(true))
        compose.runOnIdle{model.chooseProtectionFolder(tree)}
        idle{!model.state.value.busy}
        assertEquals(before,runBlocking{model.store.load()})
        assertTrue(model.state.value.protection.location.isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertFalse(app.getSharedPreferences("device",0).contains("protectionRestoreOnly"))
        // Lost/unknown picker intent is fail-safe recovery, never silent setup.
        compose.runOnIdle{model.chooseProtectionFolder(tree)};idle{!model.state.value.busy}
        assertTrue(model.state.value.protection.location.isEmpty())
    }
}
