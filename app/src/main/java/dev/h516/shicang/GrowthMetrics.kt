package dev.h516.shicang

import java.time.LocalDate
import java.time.ZoneId

internal fun collectionGrowth(library:Library,today:LocalDate=LocalDate.now()):GrowthStats {
    val active=library.active
    val from=GrowthRules.weekStart(today).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val until=today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return GrowthStats("收藏成长","把喜欢的事物，慢慢收成一本书",listOf(
        GrowthMetric("archives","在馆档案",active.size.toLong(),listOf(1,10,50,100),listOf("初次留册","小小收藏家","半百珍藏","百册藏家"),"份"),
        GrowthMetric("media","照片与视频",active.sumOf{it.photos.size}.toLong(),listOf(10,50,200),listOf("十帧记忆","光影匣子","影像收藏家"),"项"),
        GrowthMetric("tags","使用中的标签",active.flatMap{it.tags}.toSet().size.toLong(),listOf(3,10,30),listOf("分类有方","井井有条","整理达人"),"个"),
        GrowthMetric("details","有补充资料的档案",active.count{it.fields.any{f->f.value.isNotBlank()}}.toLong(),listOf(3,10,30),listOf("不止一张图","细节记录者","故事收藏家"),"份")
    ),active.count{it.updated in from until until}.toLong(),"份整理",listOf(3,7,15))
}
