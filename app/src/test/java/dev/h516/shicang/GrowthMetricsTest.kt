package dev.h516.shicang
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
class GrowthMetricsTest {
 @Test fun trashAndDraftsDoNotEarnCollectionProgress(){val today=LocalDate.of(2026,10,18);val at=today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val active=Archive(name="合成",updated=at,tags=listOf("a","b"),fields=listOf(Field(value="资料")))
  val result=collectionGrowth(Library(archives=listOf(active,active.copy(id="deleted",deleted=at)),drafts=listOf(active.copy(id="draft"))),today)
  assertEquals(1L,result.metrics.first().value);assertEquals(1L,result.weekValue);assertEquals(2L,result.metrics.first{it.key=="tags"}.value)
 }
}
