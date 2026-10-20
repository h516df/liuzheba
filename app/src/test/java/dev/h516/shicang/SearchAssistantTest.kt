package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class SearchAssistantTest {
    private val category=Label(uid(),"category","工作")
    private val tag=Label(uid(),"tag","备份")
    private val extra=Label(uid(),"tag","安全")
    private val entry=Archive(name="资料导出",category=category.id,fields=listOf(Field(name="备注",value="把备份留在本机，安全保管")),updated=10)
    private val prefs=SearchPreferences(aliases=listOf(SearchAlias("导出来","备份")))
    private val library=Library(archives=listOf(entry),labels=listOf(category,tag,extra),searchPreferences=prefs)
    @Test fun aliasNeverChangesAnyArchiveOrTag(){val before=Codec.library(library).toString();assertEquals(entry,ArchiveQuery(text="导出来").results(library).single());assertEquals(before,Codec.library(library).toString());assertTrue(ArchiveQuery(text="导出来",aliases=false).results(library).isEmpty())}
    @Test fun exactNamesAndIdsBeatAliases(){val other=Archive(name="导出来",updated=0);val l=library.copy(archives=listOf(entry,other));assertEquals(other,SearchAssistant.exact(l,"导出来").first().archive);assertEquals(entry,SearchAssistant.exact(l,entry.id).single().archive)}
    @Test fun noImplicitTransitiveOrReverseAlias(){assertTrue(SearchAssistant.exact(library.copy(archives=listOf(entry.copy(name="导出来",fields=emptyList()))),"备份").isEmpty())}
    @Test fun datesAndAllTagsConstrainEvenAliasResults(){assertTrue(ArchiveQuery(text="导出来",updatedAfter=11).results(library).isEmpty());assertTrue(ArchiveQuery(text="导出来",tags=listOf(tag.id)).results(library).isEmpty());assertTrue(ArchiveQuery(text="导出来",category="other").results(library).isEmpty())}
    @Test fun candidatesOnlyKnownVocabularyAndAtMostThree(){val tags=(1..5).map{Label(uid(),"tag","标签$it")};val a=entry.copy(fields=listOf(Field(value=tags.joinToString{it.name})));val l=library.copy(labels=tags);assertEquals(3,SearchAssistant.suggestions(l,a).size);assertTrue(SearchAssistant.suggestions(l,a).all{it.tag in tags})}
    @Test fun manualTagsAreNeverOverwrittenOrResuggested(){val a=entry.copy(tags=listOf(tag.id));assertEquals(listOf(extra),SearchAssistant.suggestions(library,a).map{it.tag});assertEquals(listOf(tag.id),a.tags)}
    @Test fun rejectionOnlyAffectsOneArchive(){val l=library.copy(searchPreferences=prefs.copy(rejections=listOf(TagRejection(entry.id,tag.id))));assertFalse(SearchAssistant.suggestions(l,entry).any{it.tag==tag});assertTrue(SearchAssistant.suggestions(l,entry.copy(id=uid())).any{it.tag==tag})}
    @Test fun neverSuggestIsScopedToCategoryAndPriorityDoesNotInventEvidence(){val r=SuggestionRule(category.id,preferred=listOf(extra.id),excluded=listOf(tag.id));val l=library.copy(searchPreferences=prefs.copy(rules=listOf(r)));assertEquals(listOf(extra),SearchAssistant.suggestions(l,entry).map{it.tag});assertTrue(SearchAssistant.suggestions(l,entry.copy(category="")).any{it.tag==tag});assertTrue(SearchAssistant.suggestions(l,entry.copy(fields=emptyList())).isEmpty())}
    @Test fun perRunOverrideDoesNotPersist(){val l=library.copy(searchPreferences=prefs.copy(rules=listOf(SuggestionRule(limit=2))));assertEquals(1,SearchAssistant.suggestions(l,entry,SuggestionRule(limit=1)).size);assertEquals(2,SearchAssistant.suggestions(l,entry).size)}
    @Test fun portablePreferencesRoundTripAndLegacyDefaults(){assertEquals(library,Codec.library(Codec.library(library)));val old=Codec.library(library).put("format",3).apply{remove("searchPreferences")};assertEquals(SearchPreferences(),Codec.library(old).searchPreferences)}
    @Test fun invalidPortablePreferencesFailClosed(){val invalid=prefs.copy(aliases=listOf(SearchAlias("a","b"),SearchAlias(" A ","c")));assertThrows(IllegalArgumentException::class.java){SearchPreferences.read(invalid.json())};assertThrows(IllegalArgumentException::class.java){SearchPreferences.read(prefs.copy(rules=listOf(SuggestionRule(limit=4))).json())}}
    @Test fun relatedIsSuggestionOnlyAndNeverReturnsSelfOrTrash(){val duplicate=entry.copy(id=uid());val l=library.copy(archives=listOf(entry,duplicate,entry.copy(id=uid(),deleted=1)));assertEquals(listOf(duplicate),SearchAssistant.related(l,entry).map{it.archive});assertEquals(3,l.archives.size)}
    @Test fun malformedCurrentPreferencesCannotSilentlyEraseExclusions(){
        val bad=Codec.library(library).put("searchPreferences","not an object")
        assertThrows(Exception::class.java){Codec.library(bad)}
        val badArray=prefs.json().put("rules","not an array")
        assertThrows(Exception::class.java){SearchPreferences.read(badArray)}
        assertThrows(Exception::class.java){Codec.library(Codec.library(library).apply{remove("searchPreferences")})}
    }
    @Test fun oldRequestCannotPublishOrClearNewRequestState(){val gate=LatestSuggestionRequest();gate.invalidate();val old=gate.current;gate.invalidate();val newer=gate.current;assertFalse(gate.isCurrent(old));assertTrue(gate.isCurrent(newer));gate.invalidate();assertFalse(gate.isCurrent(newer))}
    @Test fun aiCandidatesDisappearAfterAnySourceOrVocabularyChange(){
        val candidate=TagSuggestion(tag,"AI")
        val state=AppState(library=library,aiTagArchive=entry.id,aiTagSource=entry,aiTagLabels=library.labels,aiTagPreferences=prefs,aiTagSuggestions=listOf(candidate))
        assertEquals(listOf(candidate),visibleAiTagSuggestions(state,entry))
        assertTrue(visibleAiTagSuggestions(state,entry.copy(name="新编辑")).isEmpty())
        assertTrue(visibleAiTagSuggestions(state,entry.copy(id=uid())).isEmpty())
        assertTrue(visibleAiTagSuggestions(state.copy(library=library.copy(labels=listOf(tag.copy(name="其他含义")))),entry).isEmpty())
        assertTrue(visibleAiTagSuggestions(state.copy(library=library.copy(searchPreferences=SearchPreferences())),entry).isEmpty())
    }
}
