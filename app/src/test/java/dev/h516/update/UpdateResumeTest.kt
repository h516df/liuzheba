package dev.h516.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateResumeTest {
    @Test fun reminderIsLimitedToOneDayAndHandlesClockRollback(){
        val day=86_400_000L
        assertTrue(updateCheckDue(0,100));assertFalse(updateCheckDue(100,day));assertTrue(updateCheckDue(100,day+100));assertTrue(updateCheckDue(100,99))
    }
    @Test fun receiptRoundTripsOnlyReleaseMetadata(){
        val r=VerifiedUpdate("shicang-v0.14-universal.apk",3,"a".repeat(64),null,15,"0.14")
        assertEquals(r,VerifiedUpdate.decode(r.encode()))
    }
    @Test fun storedPathsAndUnexpectedFilesFailClosed(){
        for(name in listOf("../shicang-v0.14-universal.apk","/tmp/shicang-v0.14-universal.apk","shicang-v0.13-universal.apk","secret.apk")){
            try{VerifiedUpdate.decode(VerifiedUpdate(name,3,"a".repeat(64),null,15,"0.14").encode());fail(name)}catch(_:UpdateFailure){}
        }
    }
    @Test fun checksumUsesChosenApkNotFirstLine(){
        val text="${"a".repeat(64)}  shicang-v0.14-arm64.apk\n${"b".repeat(64)}  shicang-v0.14-universal.apk\n"
        assertEquals("b".repeat(64),UpdateVerify.parseShaFile(text,"shicang-v0.14-universal.apk"))
        try{UpdateVerify.parseShaFile(text,"unknown.apk");fail()}catch(_:UpdateFailure){}
    }
    @Test fun changedOrTruncatedFileCannotVerify(){
        val f=File.createTempFile("update-test",".apk")
        try{
            f.writeText("abc")
            val sha=MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString(""){"%02x".format(it)}
            UpdateVerify.verifyHash(f,3,sha,sha)
            f.writeText("abd")
            try{UpdateVerify.verifyHash(f,3,sha,sha);fail()}catch(_:UpdateFailure){}
            try{UpdateVerify.verifyHash(f,4,sha,sha);fail()}catch(_:UpdateFailure){}
        }finally{f.delete()}
    }
}
