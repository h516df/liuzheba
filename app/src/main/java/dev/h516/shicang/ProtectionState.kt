package dev.h516.shicang

/** “Protected” applies only to the exact persisted library last read back from SAF. */
data class ProtectionState(
    val status:String="未开启", val detail:String="目前仅保存在 App 内，清除数据或卸载会丢失", val location:String="",
    val lastSuccess:Long=0, val mediaCount:Int=0
) {
    val protected get()=status=="已保护"
    val needsAttention get()=status in listOf("保护失败","待核对")
    val showLibraryNotice get()=needsAttention
}
data class RecoveryChoice(val name:String,val time:Long,val archives:Int,val media:Int)
