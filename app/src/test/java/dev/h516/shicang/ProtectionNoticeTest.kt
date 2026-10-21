package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class ProtectionNoticeTest {
    @Test fun configuredRoutineProtectionDoesNotInterruptLibrary() {
        assertFalse(ProtectionState("待保护",location="local").showLibraryNotice)
        assertFalse(ProtectionState("已保护",location="local").showLibraryNotice)
    }
    @Test fun missingSetupAndFailuresRemainVisible() {
        assertTrue(ProtectionState().showLibraryNotice)
        assertTrue(ProtectionState("保护失败",location="local").showLibraryNotice)
        assertTrue(ProtectionState("待核对",location="local").showLibraryNotice)
        assertFalse(ProtectionState("待保护",location="local").protected)
    }
}
