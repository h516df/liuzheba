package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AuthorStoreTest {
    private lateinit var app:Application
    private lateinit var store:Store
    @Before fun start(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit();store=Store(app)}
    @After fun close()=runBlocking{store.close()}
    private fun author()=Author(name="合成作者",identities=listOf(AuthorProfiles.parse("https://weibo.com/u/123456789")!!.copy(nickname="公开合成昵称",publicAccount="合成公开账号")))
    @Test fun sameNamesRemainDistinctAndRelationshipsSurviveReopen()=runBlocking {
        val first=author();val second=author()
        store.saveAuthor(first);store.saveAuthor(second)
        val archive=Archive(name="合成档案");store.save(archive);store.linkAuthor(first.id,archive.id,true)
        val reopened=Store(app)
        try{val l=reopened.load();assertEquals(2,l.authors.size);assertEquals(listOf(first.id),l.active.single().authorIds)}finally{reopened.close()}
    }
    @Test fun linkAndUnlinkUpdateExistingDraftWithoutLosingUnsavedContent()=runBlocking {
        val author=author();store.saveAuthor(author)
        val archive=Archive(name="已保存");store.save(archive);store.draft(archive.copy(name="未保存修改"))
        store.linkAuthor(author.id,archive.id,true)
        assertEquals("未保存修改",store.load().drafts.single().name)
        assertEquals(listOf(author.id),store.load().drafts.single().authorIds)
        store.linkAuthor(author.id,archive.id,false)
        assertTrue(store.load().drafts.single().authorIds.isEmpty());assertTrue(store.load().active.single().authorIds.isEmpty())
    }
    @Test fun verifiedLinkCanSaveWithoutInventedNickname()=runBlocking {
        val a=Author(identities=listOf(AuthorProfiles.parse("https://www.douyin.com/user/synthetic_sec_uid")!!))
        store.saveAuthor(a);assertEquals("",store.load().authors.single().name);assertEquals("",store.load().authors.single().identities.single().nickname)
    }
    @Test fun importedWorksReuseTheSameVerifiedPlatformAuthorWithoutTouchingDrafts()=runBlocking {
        val profile="https://www.xiaohongshu.com/user/profile/syntheticAccount123"
        val first=Archive(name="第一条作品");val second=Archive(name="第二条作品");val third=Archive(name="已有手稿")
        store.draft(first);store.draft(second);store.save(third);store.draft(third.copy(name="未保存的文字编辑"))
        val firstId=store.linkImportedAuthor(profile,"创作者原昵称",first.id)
        val secondId=store.linkImportedAuthor(profile,"改名后的昵称",second.id)
        val thirdId=store.linkImportedAuthor(profile,"第三个昵称",third.id)
        assertEquals(firstId,secondId);assertEquals(firstId,thirdId)
        val l=store.load()
        assertEquals(1,l.authors.size)
        assertEquals("创作者原昵称",l.authors.single().name)
        assertTrue(l.drafts.all{firstId in it.authorIds})
        assertEquals("未保存的文字编辑",l.drafts.first{it.id==third.id}.name)
        assertEquals(listOf(firstId),l.active.single().authorIds)
    }
    @Test fun sameNicknamesAcrossDifferentPlatformIdsNeverMerge()=runBlocking {
        val a=Archive(name="甲");val b=Archive(name="乙")
        store.draft(a);store.draft(b)
        val one=store.linkImportedAuthor("https://www.douyin.com/user/syntheticOne","同名",a.id)
        val two=store.linkImportedAuthor("https://www.douyin.com/user/syntheticTwo","同名",b.id)
        assertNotEquals(one,two)
        assertEquals(2,store.load().authors.size)
        assertEquals(listOf(one),store.load().drafts.first{it.id==a.id}.authorIds)
        assertEquals(listOf(two),store.load().drafts.first{it.id==b.id}.authorIds)
    }
    @Test fun workUrlCanNeverBecomeAnAuthorCard()=runBlocking {
        val a=Archive(name="素材");store.draft(a)
        assertTrue(runCatching{store.linkImportedAuthor("https://www.xiaohongshu.com/explore/123","错误的作者",a.id)}.isFailure)
        assertTrue(store.load().authors.isEmpty())
        assertTrue(store.load().drafts.single().authorIds.isEmpty())
    }
    @Test fun backupRestoreAndUndoPreserveAuthorCardsAndArchiveLinks()=runBlocking {
        val provider=BackupStoreTest.BackupProvider(File(app.cacheDir,"authors.zip"))
        provider.attachInfo(app,ProviderInfo().apply{authority="authors.fixture"});ShadowContentResolver.registerProviderInternal("authors.fixture",provider)
        val uri=Uri.parse("content://authors.fixture/document/backup")
        val author=author();store.saveAuthor(author)
        val archive=Archive(name="关联档案",authorIds=listOf(author.id));store.save(archive)
        store.draft(archive.copy(name="草稿也有关联"));val before=store.load()
        store.backup(uri){}
        store.saveAuthor(author.copy(name="恢复前修改"));store.linkAuthor(author.id,archive.id,false)
        val changed=store.load()
        store.restore(uri){};assertEquals(before,store.load())
        store.undoRestore();assertEquals(changed,store.load())
    }
    @Test fun failedAssociationRollsBackNewAuthorAndKeepsDraft()=runBlocking {
        val archive=Archive(name="原草稿");store.draft(archive)
        val generation=app.getSharedPreferences("device",0).getString("generation",null)
        val db=android.database.sqlite.SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$generation/library.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("CREATE TRIGGER fail_author_draft BEFORE INSERT ON objects WHEN NEW.kind='draft' BEGIN SELECT RAISE(ABORT,'fixture failure'); END")
            assertTrue(runCatching{store.saveAuthor(author(),archive.copy(name="新草稿"))}.isFailure)
            assertTrue(store.load().authors.isEmpty());assertEquals("原草稿",store.load().drafts.single().name)
        }finally{db.close()}
    }
    @Test fun missingAuthorRejectedBeforeArchiveMutation()=runBlocking {
        val archive=Archive(name="已有资料");store.save(archive)
        assertTrue(runCatching{store.save(archive.copy(authorIds=listOf(uid())))}.isFailure)
        assertEquals(archive.name,store.load().active.single().name);assertTrue(store.load().active.single().authorIds.isEmpty())
    }
}
