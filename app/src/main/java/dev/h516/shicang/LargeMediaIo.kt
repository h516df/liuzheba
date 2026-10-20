package dev.h516.shicang

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Policy ceilings, not a promise that a device/provider has this much space or codec capacity. */
internal object LargeMediaIo {
    const val VIDEO_BYTES=6_000_000_000L // Decimal GB, matching the user-facing 6GB limit.
    const val BACKUP_BYTES=20L*1024*1024*1024
    const val RESERVE_BYTES=32L*1024*1024
    private const val SPACE_INTERVAL=8L*1024*1024

    fun requireSpace(available:Long, needed:Long=0) {
        require(needed>=0 && available>RESERVE_BYTES && needed<=available-RESERVE_BYTES) {
            "可用空间不足或无法确认剩余空间；请腾出空间后重试，原文件未改动"
        }
    }
    fun declaredSize(resolver:ContentResolver,uri:Uri):Long? = runCatching {
        resolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { c ->
            val index=c.getColumnIndex(OpenableColumns.SIZE)
            if(c.moveToFirst() && index>=0 && !c.isNull(index)) c.getLong(index).takeIf{it>=0} else null
        }
    }.getOrNull()

    class Counter(private val maximum:Long) {
        init {require(maximum>=0)}
        var bytes=0L; private set
        fun add(count:Int) {
            require(count>=0 && count.toLong()<=maximum-bytes){if(maximum==VIDEO_BYTES) "单个视频最大6GB（6,000,000,000字节），原文件未改动" else "文件实际大小超过安全上限"}
            bytes+=count
        }
    }
    /** Fixed memory even with unknown provider length; actual bytes, not metadata, enforce the limit. */
    fun copy(input:InputStream,output:OutputStream,maximum:Long,expected:Long?=null,
             available:(()->Long)?=null,check:()->Unit={},onBytes:(ByteArray,Int)->Unit={_,_->}):Long {
        require(maximum>=0)
        expected?.let {require(it in 0..maximum){"文件大小超过安全上限"}}
        available?.let {requireSpace(it(),expected ?: 0)}
        val counter=Counter(maximum);val buffer=ByteArray(65536);var checkedAt=-SPACE_INTERVAL
        while(true) {
            check()
            val count=input.read(buffer)
            if(count<0)break
            if(count==0)continue
            counter.add(count)
            expected?.let {require(counter.bytes<=it){"文件实际大小超出清单"}}
            if(available!=null && counter.bytes-checkedAt>=SPACE_INTERVAL) {
                requireSpace(available(),count.toLong());checkedAt=counter.bytes
            }
            onBytes(buffer,count);output.write(buffer,0,count)
        }
        check();expected?.let {require(counter.bytes==it){"文件未完整读取，原文件未改动"}}
        return counter.bytes
    }
    /** Guard compressed staging bytes too; incompressible ZIP output can exceed its input size. */
    fun guardedOutput(file:File,check:()->Unit):OutputStream {
        requireSpace(file.parentFile!!.usableSpace)
        val out=file.outputStream();var sinceCheck=SPACE_INTERVAL
        return object:OutputStream() {
            override fun write(value:Int){write(byteArrayOf(value.toByte()),0,1)}
            override fun write(b:ByteArray,off:Int,len:Int) {
                check();sinceCheck+=len
                if(sinceCheck>=SPACE_INTERVAL){requireSpace(file.parentFile!!.usableSpace,len.toLong());sinceCheck=0}
                out.write(b,off,len)
            }
            override fun flush()=out.flush()
            override fun close()=out.close()
        }
    }
}
