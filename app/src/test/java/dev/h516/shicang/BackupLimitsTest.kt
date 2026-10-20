package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class BackupLimitsTest {
    private val limits = BackupLimits(totalBytes=100,entries=3,manifestBytes=20,mediaBytes=60)
    private fun rejected(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("未写入备份文件"))
    }
    @Test fun exactLimitsIncludeManifestAndAcceptEmptyLibrary() {
        limits.validateExport(20,sequenceOf(40,40))
        limits.validateExport(20,emptySequence())
    }
    @Test fun rejectsEachLimitOneByteOrEntryOver() {
        rejected { limits.validateExport(21,emptySequence()) }
        rejected { limits.validateExport(20,sequenceOf(40,41)) }
        rejected { limits.validateExport(1,sequenceOf(1,1,1)) }
        rejected { limits.validateExport(1,sequenceOf(61)) }
    }
    @Test fun invalidSizesAndOverflowCannotBypassLimits() {
        rejected { limits.validateExport(-1,emptySequence()) }
        rejected { limits.validateExport(1,sequenceOf(-1)) }
        rejected { limits.validateExport(1,sequenceOf(0)) }
        rejected { limits.copy(totalBytes=Long.MAX_VALUE,mediaBytes=Long.MAX_VALUE).validateExport(1,sequenceOf(Long.MAX_VALUE)) }
    }
    @Test fun productionBoundsRejectLargeLibrariesUsingMetadataOnly() {
        val production=BackupLimits.DEFAULT
        assertEquals(LargeMediaIo.BACKUP_BYTES,production.totalBytes)
        assertEquals(100001,production.entries)
        assertEquals(32L*1024*1024,production.manifestBytes)
        production.validateExport(production.manifestBytes,sequenceOf(Codec.MAX_MEDIA_BYTES))
        rejected { production.validateExport(1,generateSequence { Codec.MAX_MEDIA_BYTES }.take(20)) }
        production.validateExport(1,generateSequence { 1L }.take(100000))
        rejected { production.validateExport(1,generateSequence { 1L }.take(100001)) }
    }
}
