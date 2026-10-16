package dev.h516.shicang

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class StoreTest {
    /** Isolated fixture directories/preferences: never clear the UI's library. */
    private fun fixture(): Context {
        val real=InstrumentationRegistry.getInstrumentation().targetContext
        val id=uid();val folder=File(real.cacheDir,"fixture-$id").apply {mkdirs()}
        return object:ContextWrapper(real) {
            override fun getFilesDir()=File(folder,"files").apply{mkdirs()}
            override fun getCacheDir()=File(folder,"cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int): SharedPreferences=real.getSharedPreferences("fixture-$id-$name",mode)
        }
    }
    @Test fun persistentNoPhotoAndLabelRenameDelete()=runBlocking {
        val c=fixture();val s=Store(c);val cat=s.label("category","亲友");val tag=s.label("tag","arm_hair")
        val a=Archive(name="妈妈",category=cat,tags=listOf(tag),fields=listOf(Field(name="居住地",value="杭州")))
        s.save(a);s.renameLabel(tag,"家人")
        val reopened=Store(c).load();assertEquals(1,reopened.find("杭州").size);assertEquals(tag,reopened.find("家人").single().tags.single());assertTrue(reopened.active.single().photos.isEmpty())
        s.deleteLabel(cat);assertEquals("",s.load().active.single().category);assertEquals(a.id,s.load().active.single().id)
    }
    @Test fun templatesAndAppearanceSurviveReopenBackupRestoreAndUndo()=runBlocking {
        val c=fixture();val s=Store(c)
        val t=Templates.create("亲友",listOf(Field(name="居住地",value="不会复制的城市"),Field(name="生日",type="日期")))
        s.saveTemplate(t);s.appearance(Appearance(theme="海盐",skin="暗房",fit="完整"))
        s.save(Archive(name="无照片人物",fields=listOf(Field())))
        assertTrue(s.load().active.single().fields.isEmpty())
        assertEquals(t,Store(c).load().templates.single())
        assertTrue(Templates.apply(Store(c).load().templates.single(),emptyList()).all{it.value.isBlank()})
        val file=File(c.cacheDir,"with-template.zip");s.backup(Uri.fromFile(file)){}
        s.deleteTemplate(t.id);s.appearance(Appearance(theme="月白",skin="留白"))
        s.restore(Uri.fromFile(file)){}
        assertEquals(t,s.load().templates.single());assertEquals("海盐",s.appearance().theme);assertEquals("暗房",s.appearance().skin)
        s.undoRestore();assertTrue(s.load().templates.isEmpty());assertEquals("月白",s.appearance().theme)
        val legacy=File(c.cacheDir,"legacy.zip")
        val manifest=Codec.library(s.load()).put("format",1).apply{remove("templates")}.put("appearance",org.json.JSONObject().put("mode","浅色").put("style","纸质相框"))
        ZipOutputStream(legacy.outputStream()).use{zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray());zip.closeEntry()}
        s.restore(Uri.fromFile(legacy)){}
        assertEquals("无照片人物",s.load().active.single().name);assertTrue(s.load().templates.isEmpty());assertEquals("留白",s.appearance().skin)
    }
    @Test fun originalCopyAndPhotoTrashAndFullBackupRestore()=runBlocking {
        val c=fixture();val s=Store(c);val original=File(c.cacheDir,"source.jpg")
        val bitmap=Bitmap.createBitmap(800,1000,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.rgb(91,124,102))}
        original.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        val p=s.importPhotos(listOf(Uri.fromFile(original))).photos.single();original.delete()
        assertTrue(File(s.photoDir,p.file).isFile);assertEquals(p.sha256,sha(File(s.photoDir,p.file)))
        val a=Archive(name="相机",photos=listOf(p),fields=listOf(Field(name="购入日期",type="日期",value="2025-02-15")))
        s.save(a);s.save(a.copy(photos=emptyList()));val l=s.load()
        assertTrue(l.active.single().photos.isEmpty());assertEquals(p,l.photoTrash.single().photo)
        s.draft(Archive(name="未完成资料"));s.appearance(Appearance(mode="深色"))
        val expected=s.load();val backup=File(c.cacheDir,"backup.zip");s.backup(Uri.fromFile(backup)){}
        s.save(Archive(name="后来添加"));s.restore(Uri.fromFile(backup)){}
        assertEquals(expected,s.load());assertEquals("深色",s.appearance().mode)
        assertEquals(p.sha256,sha(File(s.photoDir,p.file)))
        s.restorePhoto(expected.photoTrash.single().id);assertEquals(p,s.load().active.single().photos.single())
        s.undoRestore();assertEquals(2,s.load().active.size)
    }
    @Test fun badBackupLeavesExistingDataAndDoesNotExtractTraversal()=runBlocking {
        val c=fixture();val s=Store(c);s.save(Archive(name="保留资料"));val before=s.load()
        val bad=File(c.cacheDir,"bad.zip")
        ZipOutputStream(bad.outputStream()).use {zip ->
            zip.putNextEntry(ZipEntry("../overwrite.txt"));zip.write("bad".toByteArray());zip.closeEntry()
        }
        try {s.restore(Uri.fromFile(bad)){};fail("must reject traversal")} catch(e:Exception) {assertEquals(before,s.load())}
        assertFalse(File(c.filesDir,"overwrite.txt").exists())
        val good=File(c.cacheDir,"good.zip");s.backup(Uri.fromFile(good)){}
        val truncated=File(c.cacheDir,"truncated.zip").apply {writeBytes(good.readBytes().take(24).toByteArray())}
        try{s.restore(Uri.fromFile(truncated)){};fail("must reject incomplete backup")}catch(e:Exception){assertEquals(before,s.load())}
    }
    @Test fun cancelBackupAtPhotoStepPreservesLibrary()=runBlocking {
        val c=fixture();val s=Store(c);val original=File(c.cacheDir,"source.jpg")
        val bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)
        original.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        val p=s.importPhotos(listOf(Uri.fromFile(original))).photos.single()
        s.save(Archive(name="保留的相片",photos=listOf(p)));val before=s.load()
        val output=File(c.cacheDir,"canceled.zip")
        lateinit var job:kotlinx.coroutines.Job
        job=launch(start=CoroutineStart.LAZY) {s.backup(Uri.fromFile(output)){step->if(step.startsWith("打包媒体")) job.cancel()}}
        job.start();job.join()
        assertTrue(job.isCancelled);assertEquals(before,s.load());assertTrue(File(s.photoDir,p.file).exists())
        assertFalse(output.exists());assertTrue(c.cacheDir.listFiles().orEmpty().none{it.name.startsWith("backup-")})
    }
    @Test fun videoOriginalSurvivesReopenTrashBackupRestoreAndInvalidImport()=runBlocking {
        val c=fixture(); val store=Store(c)
        val input=File(c.cacheDir,"sample.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample-video.mp4").use {src->input.outputStream().use{src.copyTo(it)}}
        val hash=sha(input)
        val imported=store.importPhotos(listOf(Uri.fromFile(input)))
        assertTrue(imported.errors.toString(),imported.errors.isEmpty())
        val video=imported.photos.single(); assertTrue(video.isVideo);assertTrue(video.duration>=3000);assertEquals(hash,video.sha256)
        input.delete();assertEquals(hash,sha(File(store.photoDir,video.file)))
        val a=Archive(name="视频档案",photos=listOf(video),fields=listOf(Field(name="主页",type="链接",value="平台分享 https://v.douyin.com/test/ 来看看")))
        store.save(a)
        val reopened=Store(c).load().active.single(); assertEquals(video,reopened.photos.single());assertEquals("https://v.douyin.com/test/",reopened.fields.single().value)
        store.save(reopened.copy(photos=emptyList()));assertEquals(video,store.load().photoTrash.single().photo)
        val backup=File(c.cacheDir,"video.zip");store.backup(Uri.fromFile(backup)){}
        store.restore(Uri.fromFile(backup)){};assertEquals(video,store.load().photoTrash.single().photo)
        store.restorePhoto(store.load().photoTrash.single().id);assertEquals(hash,sha(File(store.photoDir,store.load().active.single().photos.single().file)))
        val bad=File(c.cacheDir,"bad.mp4").apply{writeText("not a video")}
        assertTrue(store.importPhotos(listOf(Uri.fromFile(bad))).photos.isEmpty())
        assertEquals(1,store.load().active.size)
        assertTrue(store.photoDir.listFiles().orEmpty().none{it.name.endsWith("pending")})
    }
    @Test fun templateUpdateKeepsIdAndValuesRemainOutsideSchema()=runBlocking {
        val c=fixture();val s=Store(c)
        val t=Templates.create("人物",listOf(Field(name="关系",value="朋友")))
        s.saveTemplate(t)
        s.save(Archive(name="原人物",fields=listOf(Field(name="关系",value="好友"))))
        s.saveTemplate(t.copy(fields=listOf(TemplateField("主页","链接"))))
        assertEquals(t.id,Store(c).load().templates.single().id)
        assertEquals("主页",s.load().templates.single().fields.single().name)
        assertEquals("好友",s.load().active.single().fields.single().value)
        assertTrue(Templates.apply(s.load().templates.single(),emptyList()).single().value.isEmpty())
    }

}
