package dev.h516.shicang

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Offline device acceptance, not a host rendering test and not a 6 GB codec benchmark.
 * The workflow selects only this class: no existing screenshot/network suites are run.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DeviceAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = instrumentation.uiAutomation
    private val prefs by lazy { context.getSharedPreferences("device", 0) }
    private var previous: Map<String, *> = emptyMap<String, Any>()
    private lateinit var store: Store
    private var activity: Activity? = null
    private val fixtures = mutableListOf<File>()
    private val documents = mutableListOf<Uri>()

    @Before fun isolate() {
        check(context.packageName == "dev.h516.shicang.verification") {
            "Device acceptance must use tools/verification.init.gradle; never the official package"
        }
        previous = prefs.all
        check(prefs.edit().clear().putString("generation", uid())
            .putBoolean("appLock", false).putBoolean("frameHintV2", true).commit())
        store = Store(context)
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    @After fun restore() = runBlocking {
        instrumentation.runOnMainSync { activity?.finish() }
        instrumentation.waitForIdleSync()
        documents.forEach { uri ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
        if (::store.isInitialized) store.close()
        fixtures.forEach { it.delete() }
        if (context.packageName == "dev.h516.shicang.verification") {
            val edit = prefs.edit().clear()
            previous.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(edit.commit())
        }
    }

    @Test fun wildcardShareImportsOrderedPhotoAndVideoThroughActualActivity() = runBlocking {
        val (_, video) = importVideo()
        val image = File(context.cacheDir, "camera/shared-${uid()}.png").also { fixtures += it }
        android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            image.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val uris = listOf(image, video).map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        start()
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setPackage(context.packageName).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .apply { clipData = android.content.ClipData.newUri(context.contentResolver, "mixed media", uris.first()).apply { addItem(android.content.ClipData.Item(uris.last())) } }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(MainActivity::class.java.name, intent.resolveActivity(context.packageManager)?.className)
        context.startActivity(intent)
        click("新建档案")
        await("ordered mixed media imported") {
            onMain { (activity as MainActivity).model.state.value.let { !it.busy && it.editor?.photos?.size == 2 } }
        }
        editName("合成混合分享档案"); click("保存")
        await("archive saved") { onMain { (activity as MainActivity).model.state.value.library.active.any { it.name == "合成混合分享档案" } } }
        val saved = store.load().active.single { it.name == "合成混合分享档案" }
        assertEquals(listOf(false, true), saved.photos.map { it.isVideo })
        assertEquals(listOf(image, video).map(::sha), saved.photos.map { it.sha256 })
        saved.photos.forEach { assertEquals(it.sha256, sha(File(store.photoDir, it.file))) }
    }

    @Test fun contentUriImportPlaysSeeksAndRetainsPositionAfterBackground() = runBlocking {
        val (photo, source) = importVideo()
        assertEquals(source.length(), photo.bytes)
        assertEquals(sha(source), photo.sha256)
        assertTrue(photo.isVideo)
        assertTrue(photo.duration >= 59000)
        val archive = Archive(name = "合成设备视频", photos = listOf(photo))
        store.save(archive)
        start()
        click("发现")
        click("播放视频")
        await("video decoder ready", 20000) {
            onMain { player()?.let { it.playbackState == Player.STATE_READY && it.playerError == null } == true }
        }
        await("playback advances") { onMain { (player()?.currentPosition ?: 0L) > 300L } }
        click("暂停")
        val before = onMain { checkNotNull(player()).currentPosition }
        click("快进10秒")
        await("forward seek") { onMain { checkNotNull(player()).currentPosition >= before + 9800 } }
        click("快退10秒")
        await("backward seek") { onMain { kotlin.math.abs(checkNotNull(player()).currentPosition - before) < 250 } }
        val retained = onMain { checkNotNull(player()).currentPosition }
        assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        Thread.sleep(500)
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        waitText("收起播放")
        await("paused position survives background") {
            onMain { player()?.let { !it.playWhenReady && kotlin.math.abs(it.currentPosition - retained) < 300 } == true }
        }
        click("收起播放")
        val resume = waitNode("saved resume affordance") { it.text?.startsWith("继续播放 · ") == true }
        click(resume)
        await("resumed decoder") { onMain { player()?.playbackState == Player.STATE_READY } }
        assertEquals(photo.sha256, sha(File(store.photoDir, photo.file)))
        assertEquals(listOf(photo), store.load().active.single().photos)
    }

    @Test fun portraitAndLandscapeVideoKeepFitAndTextReadingPreservesPause() = runBlocking {
        val (portrait, _) = importVideo("sample-portrait-video.mp4")
        val (landscape, _) = importVideo("sample-long-video.mp4")
        val archive=Archive(name="合成横竖视频档案",photos=listOf(portrait,landscape),cover=portrait.id)
        store.save(archive)
        val note=LongNote("archive",archive.id,body="这份档案同时包含横屏、竖屏视频和文字。")
        store.saveNote(note,null,store.generationToken)
        prefs.edit().putBoolean("discoveryRandomMedia",false).commit()
        start();click("发现");click("播放视频")
        await("portrait decoder ready",20000){onMain{player()?.let{it.playbackState==Player.STATE_READY && it.videoSize.height>it.videoSize.width}==true}}
        click("横屏观看")
        await("manual landscape layout"){context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
        await("rotation keeps the same video"){onMain{player()?.videoSize?.height ?: 0}>0}
        click("恢复方向")
        await("restored portrait layout"){context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
        click("暂停");val paused=onMain{checkNotNull(player()).currentPosition}
        click("阅读文字");waitText(note.body)
        assertNull(onMain{player()})
        click("返回");waitText("收起播放")
        await("reader returns to paused position"){onMain{player()?.let{!it.playWhenReady && kotlin.math.abs(it.currentPosition-paused)<500}==true}}
        click("收起播放");click("更多");click("全部媒体")
        click("查看第2项照片或视频");click("播放视频")
        await("landscape decoder ready",20000){onMain{player()?.let{it.playbackState==Player.STATE_READY && it.videoSize.width>it.videoSize.height}==true}}
        assertEquals(listOf(portrait,landscape),store.load().active.single().photos)
        assertEquals(note.body,store.load().archiveNote(archive.id)!!.body)
        listOf(portrait,landscape).forEach{assertEquals(it.sha256,sha(File(store.photoDir,it.file)))}
    }

    @Test fun media3TrimCropAndMaskProduceDecodableIndependentCopy() = runBlocking {
        val (photo, _) = importVideo()
        val original = File(store.photoDir, photo.file)
        val archive = Archive(name = "合成导出验收", photos = listOf(photo))
        store.save(archive)
        val info = VideoEditEngine.inspect(original, photo)
        // Cropping + overlay force the actual GPU/codec path, not an unchanged MP4 remux.
        val plan = VideoEditPlan(1000, 3000,
            crop = VideoRect(.25f, 0f, .75f, 1f),
            mask = VideoRect(.4f, .3f, .6f, .7f))
        val output = withTimeout(90000) {
            VideoEditEngine.export(context, original, photo, plan, info) {}
        }
        try {
            assertTrue(output.length() > 0)
            assertNotEquals(photo.sha256, sha(output))
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                val rawWidth = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
                val rawHeight = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                val rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt()
                val metadata = "rawWidth=$rawWidth rawHeight=$rawHeight rotation=$rotation"
                File(context.filesDir, "device-acceptance-media3.txt").appendText("case=trim_crop_mask $metadata\n")
                // Transformer normally encodes portrait output in landscape with rotation metadata.
                // Check the displayed crop, not just one coded dimension or a blindly swapped pair.
                assertTrue(metadata, rotation in setOf(0, 90, 180, 270))
                val displaySize = when (rotation) {
                    0, 180 -> rawWidth to rawHeight
                    90, 270 -> rawHeight to rawWidth
                    else -> error("Unsupported output orientation: $metadata")
                }
                assertEquals(metadata, info.width / 2, displaySize.first)
                assertEquals(metadata, info.height, displaySize.second)
                val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                File(context.filesDir, "device-acceptance-media3.txt").appendText(
                    "case=trim_crop_mask display=${displaySize.first}x${displaySize.second} durationMs=$duration\n")
                assertTrue("Trim duration was $duration ms", duration in 1700..2300)
                val frame = checkNotNull(reader.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST))
                try {
                    // MediaMetadataRetriever supplies the presentation-oriented bitmap on this API.
                    // Assert that contract before sampling the displayed central mask.
                    assertEquals("Decoded frame width; $metadata", displaySize.first, frame.width)
                    assertEquals("Decoded frame height; $metadata", displaySize.second, frame.height)
                    val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
                    val unmasked = frame.getPixel(frame.width / 10, frame.height / 10)
                    File(context.filesDir, "device-acceptance-media3.txt").appendText(
                        "case=trim_crop_mask decoded=${frame.width}x${frame.height} " +
                            "centerRGB=${rgb(pixel)} unmaskedRGB=${rgb(unmasked)}\n")
                    assertTrue("Fixed mask must render black",
                        android.graphics.Color.red(pixel) < 35 &&
                            android.graphics.Color.green(pixel) < 35 &&
                            android.graphics.Color.blue(pixel) < 35)
                    // The synthetic source is blue-grey. An unmasked point must retain content,
                    // rather than passing because the encoder returned an entirely black frame.
                    assertTrue("Unmasked crop region must retain content; $metadata",
                        android.graphics.Color.blue(unmasked) > 45)
                } finally { frame.recycle() }
            } finally { reader.release() }
            val edited = store.appendEditedVideo(archive.id, photo, original, output)
            assertEquals(listOf(photo, edited), store.load().active.single().photos)
            assertEquals(sha(output), edited.sha256)
            assertEquals(photo.sha256, sha(original))
        } finally { output.delete() }
    }

    @Test fun media3CropRetainsTheSelectedColorBarsAndMasksDisplayCenter() = runBlocking {
        val (photo, _) = importVideo("sample-video.mp4")
        val original = File(store.photoDir, photo.file)
        val info = VideoEditEngine.inspect(original, photo)
        assertEquals(540, info.width)
        assertEquals(960, info.height)
        val plan = VideoEditPlan(1000, 2000,
            crop = VideoRect(.25f, 0f, .75f, 1f),
            mask = VideoRect(.4f, .3f, .6f, .7f))
        val output = withTimeout(90000) {
            VideoEditEngine.export(context, original, photo, plan, info) {}
        }
        try {
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                val rawWidth = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
                val rawHeight = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                val rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt()
                val metadata = "ROI rawWidth=$rawWidth rawHeight=$rawHeight rotation=$rotation"
                File(context.filesDir, "device-acceptance-media3.txt").appendText("case=color_bar_roi $metadata\n")
                assertTrue(metadata, rotation in setOf(0, 90, 180, 270))
                val displaySize = when (rotation) {
                    0, 180 -> rawWidth to rawHeight
                    90, 270 -> rawHeight to rawWidth
                    else -> error("Unsupported output orientation: $metadata")
                }
                assertEquals(metadata, 270, displaySize.first)
                assertEquals(metadata, 960, displaySize.second)
                val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                File(context.filesDir, "device-acceptance-media3.txt").appendText(
                    "case=color_bar_roi display=${displaySize.first}x${displaySize.second} durationMs=$duration\n")
                assertTrue("ROI trim duration was $duration ms; $metadata", duration in 800..1200)
                // Output 0.5 s maps to source 1.5 s. This row avoids the synthetic moving pattern.
                val frame = checkNotNull(reader.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST))
                try {
                    assertEquals("ROI decoded frame width; $metadata", displaySize.first, frame.width)
                    assertEquals("ROI decoded frame height; $metadata", displaySize.second, frame.height)
                    fun sample(x: Float) = frame.getPixel((frame.width * x).toInt(), (frame.height * .4f).toInt())
                    val left = sample(.1f)
                    val right = sample(.9f)
                    val center = sample(.5f)
                    File(context.filesDir, "device-acceptance-media3.txt").appendText(
                        "case=color_bar_roi decoded=${frame.width}x${frame.height} " +
                            "leftRGB=${rgb(left)} rightRGB=${rgb(right)} centerRGB=${rgb(center)}\n")
                    // crop maps output x=.1/.9 to source x=.3/.7: green / magenta.
                    // 45/180 thresholds allow H.264/YUV rounding but not an adjacent color bar.
                    assertTrue("Crop left must be green; pixel=$left; $metadata",
                        android.graphics.Color.red(left) < 45 &&
                            android.graphics.Color.green(left) > 180 &&
                            android.graphics.Color.blue(left) < 45)
                    assertTrue("Crop right must be magenta; pixel=$right; $metadata",
                        android.graphics.Color.red(right) > 180 &&
                            android.graphics.Color.green(right) < 45 &&
                            android.graphics.Color.blue(right) > 180)
                    assertTrue("Display center must be masked black; pixel=$center; $metadata",
                        android.graphics.Color.red(center) < 35 &&
                            android.graphics.Color.green(center) < 35 &&
                            android.graphics.Color.blue(center) < 35)
                } finally { frame.recycle() }
            } finally { reader.release() }
            assertEquals(photo.sha256, sha(original))
        } finally { output.delete() }
    }

    @Test fun cancellingRunningMedia3ExportKeepsOriginalAndRemovesScratchFile() = runBlocking {
        val (photo, _) = importVideo()
        val original = File(store.photoDir, photo.file)
        val archive = Archive(name = "合成取消验收", photos = listOf(photo))
        store.save(archive)
        val before = store.load()
        val folder = File(context.cacheDir, "video-edits")
        val oldFiles = folder.listFiles().orEmpty().map { it.name }.toSet()
        val info = VideoEditEngine.inspect(original, photo)
        val started = CompletableDeferred<Unit>()
        val job = launch {
            val result = VideoEditEngine.export(context, original, photo,
                VideoEditPlan(0, info.durationMs, crop = VideoRect(.1f, .1f, .9f, .9f)), info) { progress ->
                // Do not pass by cancelling before the transformer actually begins producing output.
                if ((progress ?: 0) > 0 ||
                    folder.listFiles().orEmpty().any { it.name !in oldFiles && it.length() > 0 }) {
                    started.complete(Unit)
                }
            }
            result.delete()
            error("Long export finished before cancellation was exercised")
        }
        withTimeout(20000) { started.await() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        await("cancelled transformer scratch cleanup") {
            folder.listFiles().orEmpty().none { it.name !in oldFiles }
        }
        assertEquals(before, store.load())
        assertEquals(photo.sha256, sha(original))
    }

    @Test fun nativeImeArchiveCancelValidationAndSavePreserveOtherFields() = runBlocking {
        val archive = Archive(name = "合成旧信", fields = listOf(Field(name = "备注", value = "只用于设备验收")))
        store.save(archive)
        start()
        click(archive.name)
        click("编辑")
        waitText("编辑档案")
        editName("不应保存的修改")
        await("native keyboard visible") { ui.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        click("放弃修改")
        click("确认")
        await("cancel closes editor") { findText("编辑档案") == null }
        assertEquals(archive.name, store.load().active.single().name)
        assertEquals(archive.fields, store.load().active.single().fields)
        click("编辑")
        editName("")
        click("保存")
        waitText("请填写名称或称呼")
        waitText("编辑档案")
        assertEquals(archive.name, store.load().active.single().name)
        editName("合成已保存旧信")
        click("保存")
        await("save closes editor") { findText("编辑档案") == null }
        waitText("合成已保存旧信")
        val saved = store.load().active.single()
        assertEquals("合成已保存旧信", saved.name)
        assertEquals(archive.fields, saved.fields)
        assertTrue(store.load().drafts.isEmpty())
    }

    @Test fun systemSafBackupCancelThenSaveRoundTripsSyntheticMedia() = runBlocking {
        val (photo, _) = importVideo()
        val archive = Archive(name = "合成 SAF 档案", photos = listOf(photo))
        store.save(archive)
        val before = store.load()
        start()
        click("我的")
        clickScrolling("备份与恢复")
        clickScrolling("导出备份文件（换机或留存）")
        click("确认")
        waitDocumentsUi()
        // DocumentsUI may first consume Back to dismiss its filename keyboard.
        repeat(2) {
            if (documentNodes().isNotEmpty()) {
                assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                ui.waitForIdle(700, 6000)
            }
        }
        await("cancel returns to the real backup page") {
            documentNodes().isEmpty() && findText("导出备份文件（换机或留存）") != null
        }
        assertEquals(0L, store.lastBackup())
        assertFalse(prefs.contains("backupLocation"))
        assertEquals(before, store.load())

        clickScrolling("导出备份文件（换机或留存）")
        click("确认")
        waitDocumentsUi()
        val name = waitNode("SAF filename") { it.isEditable && isDocumentNode(it) }
        setText(name, "device-acceptance-${uid()}.zip")
        val save = waitNode("SAF save action") {
            isDocumentNode(it) && it.isEnabled &&
                (it.viewIdResourceName?.endsWith(":id/action_menu_save") == true ||
                    it.text?.toString()?.equals("Save", ignoreCase = true) == true ||
                    it.text?.toString() == "保存")
        }
        click(save)
        await("SAF output written and read-back verified", 30000) { store.lastBackup() > 0 }
        val uri = Uri.parse(checkNotNull(prefs.getString("backupLocation", null)))
        documents += uri
        assertEquals("content", uri.scheme)
        var manifest: Library? = null
        var mediaHash: String? = null
        context.contentResolver.openInputStream(uri).use { input ->
            ZipInputStream(checkNotNull(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        "manifest.json" -> manifest = Codec.library(JSONObject(zip.readBytes().toString(Charsets.UTF_8)))
                        "photos/${photo.file}" -> mediaHash = hashStream(zip)
                    }
                    zip.closeEntry()
                }
            }
        }
        assertEquals(before, manifest)
        assertEquals(photo.sha256, mediaHash)
        assertEquals(before, store.load())
    }

    private suspend fun importVideo(asset: String = "sample-long-video.mp4"): Pair<Photo, File> {
        val input = File(context.cacheDir, "camera/device-${uid()}.mp4").also {
            check(it.parentFile!!.mkdirs() || it.parentFile!!.isDirectory)
            fixtures += it
        }
        instrumentation.context.assets.open(asset).use { source ->
            input.outputStream().use { source.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", input)
        assertEquals("content", uri.scheme)
        val imported = store.importPhotos(listOf(uri))
        assertTrue(imported.errors.toString(), imported.errors.isEmpty())
        return imported.photos.single() to input
    }

    private fun rgb(pixel: Int) = listOf(android.graphics.Color.red(pixel),
        android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)).joinToString(",")

    private fun start() {
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitText("搜索档案")
    }

    private fun player(view: View = checkNotNull(activity).window.decorView): Player? {
        if (view is PlayerView) return view.player
        if (view is ViewGroup) repeat(view.childCount) { index -> player(view.getChildAt(index))?.let { return it } }
        return null
    }

    private fun <T> onMain(block: () -> T): T {
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        ui.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) result += node
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(ui.rootInActiveWindow)
        return result
    }

    private fun findText(text: String) = nodes().firstOrNull {
        it.text?.toString() == text || it.contentDescription?.toString() == text
    }

    private fun await(label: String, timeout: Long = 12000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            findText("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(100)
        }
        error("Timed out: $label. Visible synthetic UI: " +
            nodes().mapNotNull { it.text ?: it.contentDescription }.take(35).joinToString(" | "))
    }

    private fun waitNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        await(label) { result = nodes().firstOrNull(predicate); result != null }
        return checkNotNull(result)
    }

    private fun waitText(text: String) = waitNode(text) {
        it.text?.toString() == text || it.contentDescription?.toString() == text
    }

    private fun click(text: String) {
        // Closing the editor precedes the final data reload. Wait for the real enabled control,
        // and reacquire stale accessibility nodes; never treat a rejected click as an action.
        await("enabled control $text") {
            var node: AccessibilityNodeInfo? = findText(text)
            while (node != null && !node.isClickable) node = node.parent
            node?.let { it.isEnabled && it.isVisibleToUser &&
                it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
        }
        Thread.sleep(250)
    }

    private fun click(target: AccessibilityNodeInfo) {
        var node: AccessibilityNodeInfo? = target
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Control is not clickable: ${target.text}" }
        Thread.sleep(250)
    }

    private fun clickScrolling(text: String) {
        await("scroll to $text", 20000) {
            if (findText(text) != null) true else {
                // During activity return there may briefly be no app window or scroll container.
                // Wait for the real page; an absent container is not a completed navigation.
                nodes().filter { it.isScrollable }.any {
                    it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
                false
            }
        }
        click(text)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        Thread.sleep(200)
    }

    private fun editName(value: String) {
        val name = waitNode("archive name field") { it.isEditable }
        click(name)
        setText(name, value)
    }

    private fun isDocumentNode(node: AccessibilityNodeInfo) =
        node.packageName?.toString() in setOf("com.android.documentsui", "com.google.android.documentsui")

    private fun documentNodes() = nodes().filter(::isDocumentNode)

    private fun waitDocumentsUi() = await("system DocumentsUI, not an in-app substitute") { documentNodes().isNotEmpty() }
}
