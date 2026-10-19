package dev.h516.update

import org.json.JSONObject

/** Only public release metadata is persisted. Paths are never accepted from storage. */
internal data class VerifiedUpdate(val name:String,val size:Long,val sha:String,val digest:String?,val code:Int,val version:String) {
    fun encode():String=JSONObject().put("name",name).put("size",size).put("sha",sha).put("digest",digest).put("code",code).put("version",version).toString()
    companion object {
        fun decode(text:String):VerifiedUpdate {
            val j=JSONObject(text)
            val r=VerifiedUpdate(j.getString("name"),j.getLong("size"),j.getString("sha"),j.optString("digest").takeIf{it.isNotEmpty()},j.getInt("code"),j.getString("version"))
            requireUpdate(r.version.matches(Regex("[0-9]+\\.[0-9]+")) && r.code>0)
            requireUpdate(r.name in UpdateConfig.apkNames(r.version,listOf("arm64-v8a")))
            requireUpdate(r.size in 1..UpdateConfig.MAX_APK && r.sha.matches(Regex("[a-f0-9]{64}")))
            requireUpdate(r.digest==null || r.digest.matches(Regex("[a-f0-9]{64}")))
            return r
        }
    }
}
internal fun updateCheckDue(last:Long,now:Long):Boolean=last==0L || now<last || now-last>=24*60*60*1000L
