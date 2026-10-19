package dev.h516.shicang
import org.junit.Assert.*
import org.junit.Test
class ArchiveQueryTest {
    private val a=Archive(id="a",name="Alpha",category="c",tags=listOf("t","u"),fields=listOf(Field(name="备注",value="森林")),updated=2)
    private val b=Archive(id="b",name="Beta",category="d",tags=listOf("t"),updated=3)
    private val library=Library(archives=listOf(a,b,a.copy(id="deleted",deleted=1)),labels=listOf(Label("c","category","日常"),Label("t","tag","自然"),Label("u","tag","旅行")))
    @Test fun combinationRequiresEverySelectedTag(){assertEquals(listOf(a),ArchiveQuery(tags=listOf("t","u")).results(library));assertTrue(ArchiveQuery(category="d",tags=listOf("u")).results(library).isEmpty())}
    @Test fun textSearchIncludesLabelsAndFields(){assertEquals(listOf(a),ArchiveQuery(text=" 森林 ").results(library));assertEquals(listOf(a),ArchiveQuery(text="日常").results(library))}
    @Test fun clearConditionsRetainsAllActiveArchives(){assertEquals(listOf(b,a),ArchiveQuery().results(library));assertFalse(ArchiveQuery(text="  ").filtered);assertTrue(ArchiveQuery(tags=listOf("t")).filtered)}
    @Test fun nameAndUpdatedSortAreDeterministic(){assertEquals(listOf(a,b),ArchiveQuery(byName=true).results(library));assertEquals(listOf(b,a),ArchiveQuery().results(library))}
    @Test fun captionsAreBoundedAndPreferLabels(){assertEquals("日常 · 自然 +1",archiveCaption(a,library));assertEquals("森林",archiveCaption(a.copy(category="",tags=emptyList()),library))}
    @Test fun nameOnlyEntryDoesNotPretendPhotoIsMissing(){assertEquals("",archiveCaption(Archive(name="旧信"),Library()))}
    @Test fun deletedOrWrongKindFiltersAreRemovedAfterRestore(){
        val stale=ArchiveQuery(text="森林",category="gone",tags=listOf("t","gone","c","t"),byName=true)
        val clean=stale.normalized(library)
        assertEquals("",clean.category);assertEquals(listOf("t"),clean.tags)
        assertEquals("森林",clean.text);assertTrue(clean.byName)
        assertEquals(listOf(a),clean.results(library))
        assertFalse(ArchiveQuery(category="gone",tags=listOf("gone")).normalized(library).filtered)
    }
}
