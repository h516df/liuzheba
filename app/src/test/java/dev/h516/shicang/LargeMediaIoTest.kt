package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.CancellationException

class LargeMediaIoTest {
    private val gib=1024L*1024*1024
    private class Generated(private var left:Long):InputStream() {
        override fun read():Int=if(left-- >0)0 else -1
        override fun read(b:ByteArray,off:Int,len:Int):Int {if(left==0L)return -1;val n=minOf(left,len.toLong()).toInt();java.util.Arrays.fill(b,off,off+n,0.toByte());left-=n;return n}
    }
    private val sink=object:OutputStream(){override fun write(b:Int){};override fun write(b:ByteArray,off:Int,len:Int){}}
    @Test fun streamedUnknownLengthPassesOneTwoAndFourGiBBoundariesWithFixedBuffer() {
        val size=4*gib+65537
        var maximumChunk=0
        assertEquals(size,LargeMediaIo.copy(Generated(size),sink,Codec.MAX_MEDIA_BYTES,onBytes={_,n->maximumChunk=maxOf(maximumChunk,n)}))
        assertEquals(65536,maximumChunk)
    }
    @Test fun longCountersRejectOverflowAndPolicyOverageBeforeWriting() {
        val c=LargeMediaIo.Counter(Long.MAX_VALUE)
        // Long-sized boundary coverage without allocating files or 2^63 bytes.
        val boundary=LargeMediaIo.Counter(4*gib+2)
        boundary.add(Int.MAX_VALUE);boundary.add(Int.MAX_VALUE);boundary.add(4)
        assertEquals(4*gib+2,boundary.bytes)
        assertTrue(runCatching{boundary.add(1)}.isFailure)
        assertTrue(runCatching{c.add(-1)}.isFailure)
        assertTrue(runCatching{LargeMediaIo.copy(Generated(101),sink,100)}.isFailure)
    }
    @Test fun exactSixDecimalGBIsAllowedAndOneByteOverRejected() {
        assertEquals(6_000_000_000L,Codec.MAX_MEDIA_BYTES)
        assertEquals(6_000_000_000L,LargeMediaIo.copy(Generated(6_000_000_000L),sink,Codec.MAX_MEDIA_BYTES))
        assertTrue(runCatching{LargeMediaIo.copy(Generated(6_000_000_001L),sink,Codec.MAX_MEDIA_BYTES)}.isFailure)
        BackupLimits.DEFAULT.validateExport(100,sequenceOf(6_000_000_000L))
        assertTrue(runCatching{BackupLimits.DEFAULT.validateExport(100,sequenceOf(6_000_000_001L))}.isFailure)
    }
    @Test fun declaredLargeFilesDoNotStartWithoutRoomAndUnknownSpaceFailsClosed() {
        var reads=0
        val input=object:InputStream(){override fun read():Int{reads++;return -1}}
        assertTrue(runCatching{LargeMediaIo.copy(input,sink,Codec.MAX_MEDIA_BYTES,2*gib,{gib})}.isFailure)
        assertEquals(0,reads)
        listOf(-1L,0L,LargeMediaIo.RESERVE_BYTES).forEach { assertTrue(runCatching{LargeMediaIo.requireSpace(it)}.isFailure) }
    }
    @Test fun ongoingSpaceLossAndCancellationStopTheStream() {
        var writes=0L;var polls=0
        val out=object:OutputStream(){override fun write(b:Int){};override fun write(b:ByteArray,off:Int,len:Int){writes+=len}}
        assertTrue(runCatching{LargeMediaIo.copy(Generated(gib),out,gib,available={if(++polls<3)gib else 0})}.isFailure)
        assertTrue(writes<=8L*1024*1024)
        var checks=0
        val e=runCatching{LargeMediaIo.copy(Generated(gib),sink,gib,check={if(++checks==4)throw CancellationException("fixture")})}.exceptionOrNull()
        assertTrue(e is CancellationException)
    }
    @Test fun lyingAndTruncatedStreamsCannotPassExactManifestSize() {
        assertTrue(runCatching{LargeMediaIo.copy(Generated(9),sink,100,10)}.isFailure)
        assertTrue(runCatching{LargeMediaIo.copy(Generated(11),sink,100,10)}.isFailure)
    }
    @Test fun editReservationDoesNotOverflowAtLongOrFourGiBBoundaries() {
        val plan=VideoEditPlan(0,1000)
        listOf(gib+1,2*gib+1,4*gib+1,Long.MAX_VALUE).forEach {assertTrue(plan.requiredBytes(it)>0)}
        assertTrue(plan.requiredBytes(4*gib)>2*Codec.MAX_MEDIA_BYTES)
    }
    /** Real ZIP64 encoding/decoding of a >4GiB generated entry, not a video/codec test. */
    @Test fun zip64GeneratedEntryOverFourGiBStreamsRoundTrip() {
        val size=4*gib+1;val zipFile=File.createTempFile("zip64-generated-",".zip")
        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("generated.bin"))
                LargeMediaIo.copy(Generated(size),zip,size);zip.closeEntry()
            }
            assertTrue(zipFile.length()<8*1024*1024)
            ZipFile(zipFile).use { assertEquals(size,it.getEntry("generated.bin").size) }
            ZipInputStream(zipFile.inputStream().buffered()).use {zip ->
                assertEquals("generated.bin",zip.nextEntry.name)
                assertEquals(size,LargeMediaIo.copy(zip,sink,size))
                assertNull(zip.nextEntry)
            }
        } finally {zipFile.delete()}
    }
}
