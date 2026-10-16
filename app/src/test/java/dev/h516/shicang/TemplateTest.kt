package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class TemplateTest {
    @Test fun schemaDoesNotContainPersonalValuesAndFreshApplicationIsBlank() {
        val t=Templates.create("亲友",listOf(Field(name="居住地",value="私人城市"),Field(name="生日",type="日期",value="2001-01-02"),Field()))
        assertFalse(Templates.json(t).toString().contains("私人城市"))
        assertFalse(Templates.json(t).toString().contains("2001-01-02"))
        assertEquals(listOf("居住地","生日"),Templates.apply(t,emptyList()).map{it.name})
        assertTrue(Templates.apply(t,emptyList()).all{it.value.isEmpty()})
    }
    @Test fun applyingTwiceRetainsValuesAndDoesNotDuplicateFields() {
        val t=Templates.create("亲友",listOf(Field(name="居住地")))
        val current=listOf(Field(name="居住地",value="已填内容"),Field(name="备注",value="保留备注"))
        val once=Templates.apply(t,current)
        assertEquals(current,once);assertEquals(once,Templates.apply(t,once))
    }
    @Test fun typeConflictKeepsFilledDraftWithoutDuplicateNames() {
        val t=Templates.create("日期模板",listOf(Field(name="生日",type="日期")))
        val old=Field(name="生日",type="文本",value="农历八月")
        assertEquals(listOf(old),Templates.apply(t,listOf(old)))
        assertEquals("日期",Templates.apply(t,listOf(old.copy(value=""))).single().type)
    }
    @Test fun backupIncludesTemplatesAndLegacyBackupStillOpens() {
        val l=Library(archives=listOf(Archive(name="无照片人物")),templates=listOf(Templates.create("联系",listOf(Field(name="主页",type="链接")))))
        assertEquals(l,Codec.library(Codec.library(l)))
        val old=Codec.library(l).put("format",1).apply{remove("templates")}
        assertEquals(l.archives,Codec.library(old).archives);assertTrue(Codec.library(old).templates.isEmpty())
    }
    @Test fun rejectsDuplicateAndUnnamedFieldsWithContents() {
        assertThrows(IllegalArgumentException::class.java){Templates.create("模板",listOf(Field(name="地址"),Field(name=" 地址 ")))}
        assertThrows(IllegalArgumentException::class.java){Templates.create("模板",listOf(Field(value="内容")))}
        assertThrows(IllegalArgumentException::class.java){Templates.create("模板",emptyList())}
    }
    @Test fun appearanceRoundtripAndLegacyMigrationPreservesCompleteImage() {
        val a=Appearance(theme="海盐",skin="暗房",fit="完整")
        assertEquals(a,Appearance.read(a.json()))
        val old=org.json.JSONObject().put("mode","浅色").put("style","纸质相框")
        assertEquals("月白",Appearance.read(old).theme);assertEquals("留白",Appearance.read(old).skin);assertEquals("完整",Appearance.read(old).fit)
    }
}
