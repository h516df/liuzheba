package dev.h516.shicang

import org.junit.Test
import org.junit.Assert.*

class LongNotesTest {
    @Test fun longMultilineRoundTripKeepsShortFields() {
        val a=Archive(name="合成档案",fields=listOf(Field(name="旧备注",value="保持原样")))
        val note=LongNote("archive",a.id,"故事","第一段\n"+"合成文字".repeat(10000),123)
        val l=Library(archives=listOf(a),notes=listOf(note),noteDrafts=listOf(note.copy(body="未提交草稿")))
        assertEquals(l,Codec.library(Codec.library(l)))
        assertEquals("保持原样",Codec.library(Codec.library(l)).active.single().fields.single().value)
    }
    @Test fun oldBackupHasNoNotesButKeepsFields() {
        val a=Archive(name="旧档案",fields=listOf(Field(value="旧文字")))
        for(v in 1..5){val json=Codec.library(Library(archives=listOf(a))).put("format",v);json.remove("notes");json.remove("noteDrafts");assertTrue(Codec.library(json).notes.isEmpty());assertEquals(a,Codec.library(json).archives.single())}
    }
    @Test fun noSilentTruncationAndStrictJson() {
        val n=LongNote("archive",uid(),body="合".repeat(LongNote.BODY_LIMIT))
        assertEquals(LongNote.BODY_LIMIT,LongNote.read(n.json()).body.length)
        assertTrue(runCatching{n.copy(body=n.body+"超").validated()}.isFailure)
        assertTrue(runCatching{LongNote.read(n.json().put("body",42))}.isFailure)
        assertTrue(runCatching{n.copy(type="file",target="../../x").validated()}.isFailure)
    }
    @Test fun searchFindsArchiveMediaAndAuthorNotesWithoutDraftLeak() {
        val author=Author(name="合成作者");val p=Photo(uid(),"unused",1,"a".repeat(64),"image/png")
        val a=Archive(name="合成档案",photos=listOf(p),authorIds=listOf(author.id))
        val l=Library(archives=listOf(a),authors=listOf(author),notes=listOf(LongNote("archive",a.id,body="雨夜"),LongNote("media",p.id,body="海边"),LongNote("author",author.id,body="手作")),noteDrafts=listOf(LongNote("archive",a.id,body="秘密草稿")))
        listOf("雨夜","海边","手作").forEach{assertEquals(a,l.find(it).single())};assertTrue(l.find("秘密草稿").isEmpty())
    }
    @Test fun orphanNotesAreRetainedAndClearlyLabeled() {
        val n=LongNote("media",uid(),body="不应丢失")
        val l=Codec.library(Codec.library(Library(notes=listOf(n))))
        assertEquals(n,l.notes.single());assertTrue(l.noteLabel(n).contains("已移除"))
    }
    @Test fun duplicateNoteTargetRejected() {
        val n=LongNote("archive",uid(),body="合成")
        assertTrue(runCatching{Codec.library(Codec.library(Library(notes=listOf(n,n))))}.isFailure)
    }
}
