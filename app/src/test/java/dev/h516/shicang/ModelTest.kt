package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import kotlin.random.Random

class ModelTest {
    @Test fun addingArchivesPreservesVisibleArchiveInRoundAndHistory() {
        val deck=ShuffleDeck(Random(11))
        deck.setIds(listOf("a"));assertEquals("a",deck.next())
        deck.setIds(listOf("a","b","c"))
        assertEquals("a",deck.previous())
        val remainder=listOf(deck.next(),deck.next())
        assertEquals(setOf("b","c"),remainder.toSet())
        deck.setIds(listOf("b"))
        assertEquals("b",deck.next())
    }
    @Test fun noPhotoIsACompleteSearchableArchiveAndSurvivesBackupFormat() {
        val a=Archive(name="妈妈",fields=listOf(Field(name="居住地",value="杭州"),Field(name="与我的关系",value="母亲")))
        val l=Codec.library(Codec.library(Library(archives=listOf(a))))
        assertEquals(a,l.archives.single());assertTrue(l.find("杭州").single().photos.isEmpty());assertEquals(a.id,l.find("母亲").single().id)
    }
    @Test fun searchTreatsSymbolsLiterallyAndUsesRenamedStableLabels() {
        val t=Label(uid(),"tag","arm_hair");val a=Archive(name="100%棉",tags=listOf(t.id))
        val l=Library(archives=listOf(a,Archive(name="1000棉")),labels=listOf(t))
        assertEquals(1,l.find("100%").size);assertEquals(0,l.find("armchair").size)
        assertEquals(a.id,l.copy(labels=listOf(t.copy(name="亲友"))).find("亲友").single().id)
    }
    @Test fun sameNamesStaySeparateAndTrashIsExcluded() {
        val a=Archive(name="老王");val b=Archive(name="老王");val c=Archive(name="老王",deleted=1)
        assertEquals(setOf(a.id,b.id),Library(archives=listOf(a,b,c)).find("老王").map{it.id}.toSet())
    }
    @Test fun shuffleIsByArchiveAndRoundDoesNotRepeatAndHistoryIsReal() {
        val deck=ShuffleDeck(Random(123));val ids=(1..12).map {uid()};deck.setIds(ids)
        val first=(1..12).map{deck.next()};assertEquals(ids.toSet(),first.toSet())
        assertEquals(first[10],deck.previous());assertEquals(first[11],deck.next())
        val next=deck.next();assertNotEquals(first.last(),next)
        deck.setIds(listOf(ids[0]));repeat(4){assertEquals(ids[0],deck.next())}
        deck.setIds(emptyList());assertNull(deck.next());assertNull(deck.previous())
    }
    @Test fun removedCoverFallsBackButNoPhotoStaysValid() {
        val p=Photo(uid(),"unused",10,"a".repeat(64),"image/jpeg")
        assertEquals(p,Archive(name="相机",cover="missing",photos=listOf(p)).coverPhoto())
        assertNull(Archive(name="妈妈").coverPhoto())
    }
    @Test fun rejectsPhotoPathTraversalAndMissingLabelsAndUnknownFormat() {
        val id=uid();val p=JSONObject().put("id",id).put("file","../../secret").put("bytes",10).put("sha256","a".repeat(64)).put("mime","image/jpeg")
        assertThrows(IllegalArgumentException::class.java){Codec.photo(p)}
        val l=Codec.library(Library(archives=listOf(Archive(name="人物",tags=listOf(uid())))))
        assertThrows(IllegalArgumentException::class.java){Codec.library(l)}
        assertThrows(IllegalArgumentException::class.java){Codec.library(Codec.library(Library()).put("format",999))}
    }
}
