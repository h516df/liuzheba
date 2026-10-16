package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class BrowseTest {
    @Test fun keepsRecentlyViewedPhotosWhenHistoryReachesLimit() {
        var positions:Map<String,String> = emptyMap()
        repeat(200){positions=rememberPhotoPosition(positions,"archive$it","photo$it")}
        positions=rememberPhotoPosition(positions,"archive0","newPhoto")
        positions=rememberPhotoPosition(positions,"archive200","photo200")
        assertEquals(200,positions.size)
        assertEquals("newPhoto",positions["archive0"])
        assertEquals("photo200",positions["archive200"])
        assertFalse(positions.containsKey("archive1"))
    }
    private fun photo() = Photo(uid(), "test", 10, "a".repeat(64), "image/jpeg")
    @Test fun settingsReturnUsesLatestPageAndRejectsRemovedPosition() {
        val p=photo();val q=photo();val a=Archive(name="照片",photos=listOf(p,q))
        val l=Library(archives=listOf(a))
        assertEquals(q.id,settingsPhotoPosition(l,mapOf(a.id to q.id),p.id))
        assertEquals(p.id,settingsPhotoPosition(l,mapOf(a.id to "removed"),p.id))
        assertEquals(p.id,settingsPhotoPosition(l,emptyMap(),p.id))
        assertEquals("",settingsPhotoPosition(l,mapOf(a.id to q.id),""))
    }
    @Test fun positionFollowsPhotoIdentityAndFallsBackAfterRemoval() {
        val p=photo(); val q=photo(); val a=Archive(name="照片",photos=listOf(p,q),cover=q.id)
        assertEquals(1,photoIndex(a,q.id))
        assertEquals(0,photoIndex(a.copy(photos=listOf(q,p)),q.id))
        assertEquals(0,photoIndex(a.copy(photos=listOf(p)),q.id))
        assertEquals(1,photoIndex(a,"unknown"))
        assertEquals(0,photoIndex(a.copy(photos=emptyList()),q.id))
    }
    @Test fun gestureLocksOnlyAfterDirectionIsClear() {
        assertEquals(SwipeAxis.Undecided,swipeAxis(4f,3f,8f))
        assertEquals(SwipeAxis.Undecided,swipeAxis(30f,30f,8f))
        assertEquals(SwipeAxis.Horizontal,swipeAxis(-50f,20f,8f))
        assertEquals(SwipeAxis.Vertical,swipeAxis(10f,-60f,8f))
    }
}
