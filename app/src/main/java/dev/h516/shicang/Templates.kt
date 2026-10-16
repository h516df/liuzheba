package dev.h516.shicang

import org.json.JSONArray
import org.json.JSONObject

data class TemplateField(val name:String, val type:String="文本")
data class FieldTemplate(val id:String=uid(), val name:String, val fields:List<TemplateField>)

object Templates {
    val types=listOf("文本","数字","日期","链接")
    fun create(name:String,fields:List<Field>):FieldTemplate {
        val schema=fields.filter{it.name.isNotBlank() || it.value.isNotBlank()}.map{TemplateField(it.name.trim(),it.type)}
        return FieldTemplate(name=name.trim(),fields=schema).also(::validate)
    }
    fun validate(t:FieldTemplate) {
        require(Codec.validId(t.id) && t.name.isNotBlank() && t.name.length<=40) {"模板名称需要 1–40 个字"}
        require(t.fields.size in 1..40 && t.fields.all{it.name.isNotBlank() && it.name.length<=40 && it.type in types}) {"请填写字段名称，最多 40 个字段"}
        require(t.fields.map{normalizedLabel(it.name)}.distinct().size==t.fields.size) {"字段名称不能重复"}
    }
    fun apply(t:FieldTemplate,current:List<Field>):List<Field> {
        validate(t)
        val used=mutableSetOf<String>()
        val applied=t.fields.map {schema ->
            val match=current.firstOrNull{normalizedLabel(it.name)==normalizedLabel(schema.name)}
            if(match!=null) {used.add(match.id);match.copy(name=schema.name,type=if(match.value.isBlank()) schema.type else match.type)} else Field(name=schema.name,type=schema.type)
        }
        return applied+current.filter{it.id !in used && (it.name.isNotBlank() || it.value.isNotBlank())}
    }
    fun json(t:FieldTemplate):JSONObject {validate(t);return JSONObject().put("id",t.id).put("name",t.name).put("fields",JSONArray(t.fields.map{JSONObject().put("name",it.name).put("type",it.type)}))}
    fun read(o:JSONObject)=FieldTemplate(o.getString("id"),o.getString("name"),o.getJSONArray("fields").objects().map{TemplateField(it.getString("name"),it.getString("type"))}).also(::validate)
}
