package dev.h516.shicang

/** Uncompressed limits shared by the backup writer and reader. Includes manifest.json. */
internal data class BackupLimits(
    val totalBytes: Long = LargeMediaIo.BACKUP_BYTES,
    val entries: Int = 100001,
    val manifestBytes: Long = 32L * 1024 * 1024,
    val mediaBytes: Long = Codec.MAX_MEDIA_BYTES,
) {
    fun validateExport(manifestSize: Long, mediaSizes: Sequence<Long>) {
        require(manifestSize in 0..manifestBytes) { "备份清单超过恢复上限，未写入备份文件；请保留原有资料" }
        var total = 0L
        var count = 0
        fun add(size: Long) {
            require(count < entries) { "备份文件数量超过恢复上限，未写入备份文件；请保留原有资料" }
            require(size >= 0 && size <= totalBytes - total) { "备份展开容量超过恢复上限（单份备份最多20GiB，不是媒体库总容量），未写入备份文件；请保留原有资料" }
            total += size
            count++
        }
        add(manifestSize)
        mediaSizes.forEach { size ->
            require(size in 1..mediaBytes) { "媒体大小超过恢复上限，未写入备份文件；请保留原有资料" }
            add(size)
        }
    }

    companion object { val DEFAULT = BackupLimits() }
}
