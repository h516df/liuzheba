package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class DiscoveryPreviewTest {
    private fun photo(id:String,mime:String="image/png")=Photo(id,"$id.original",1,"a".repeat(64),mime)
    private val a=Archive(id="archive-a",name="合成档案",photos=listOf(photo("p1"),photo("p2","video/mp4"),photo("p3")),cover="p1")
    @Test fun visitsEachMediaBeforeRepeating() {
        val rounds=(0..8).map{discoveryPreview(a,it,42,true)!!.id}
        assertEquals(3,rounds.take(3).toSet().size)
        assertEquals(rounds.take(3),rounds.drop(3).take(3))
        assertTrue(rounds.zipWithNext().none{it.first==it.second})
        assertTrue(rounds.contains("p2"))
    }
    @Test fun recompositionAndMediaReorderDoNotChangePreview() {
        assertEquals(discoveryPreview(a,2,42,true),discoveryPreview(a.copy(photos=a.photos.reversed()),2,42,true))
        repeat(20){assertEquals(discoveryPreview(a,1,42,true),discoveryPreview(a,1,42,true))}
    }
    @Test fun sessionCanChangePreviewButNeverCrossArchive() {
        assertTrue((0L..40L).map{discoveryPreview(a,0,it,true)!!.id}.toSet().size>1)
        assertTrue((0..40).all{discoveryPreview(a,it,12,true)!!.id in a.photos.map{p->p.id}})
    }
    @Test fun fixedCoverAndMissingCoverFallback() {
        assertEquals("p1",discoveryPreview(a,99,1,false)!!.id)
        assertEquals("p2",discoveryPreview(a.copy(photos=a.photos.drop(1)),0,1,false)!!.id)
    }
    @Test fun excludedArchivesAndNoAvailableMediaShowNothing() {
        assertNull(discoveryPreview(a.copy(deleted=1),0,1,true))
        assertNull(discoveryPreview(a.copy(photos=emptyList()),0,1,true))
    }
    @Test fun availableSetAndDuplicatesNeverReviveUnavailableMedia() {
        val filtered=a.copy(photos=listOf(a.photos[1],a.photos[1]))
        assertEquals(setOf("p2"),(0..5).map{discoveryPreview(filtered,it,1,true)!!.id}.toSet())
    }
    @Test fun archiveOrderIsStableAndEachRoundContainsEveryArchive() {
        val ids=listOf("b","a","c","a")
        assertEquals(discoveryOrder(ids,17),discoveryOrder(ids.reversed(),17))
        assertEquals(setOf("a","b","c"),discoveryOrder(ids,17).toSet())
        assertEquals(listOf("b")+discoveryOrder(ids,17).filterNot{it=="b"},discoveryOrder(ids,17,"b"))
    }
}
