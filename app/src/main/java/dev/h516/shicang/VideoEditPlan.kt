package dev.h516.shicang

/** Top-left-origin coordinates in the upright video, independent of preview size. */
internal data class VideoRect(val left:Float=0f,val top:Float=0f,val right:Float=1f,val bottom:Float=1f) {
    fun valid()=listOf(left,top,right,bottom).all{it.isFinite() && it in 0f..1f} && right-left>=.02f && bottom-top>=.02f
    fun cropCoordinates()=floatArrayOf(left*2-1,right*2-1,1-bottom*2,1-top*2)
    companion object {
        fun between(x1:Float,y1:Float,x2:Float,y2:Float)=VideoRect(minOf(x1,x2).coerceIn(0f,1f),minOf(y1,y2).coerceIn(0f,1f),maxOf(x1,x2).coerceIn(0f,1f),maxOf(y1,y2).coerceIn(0f,1f))
    }
}
internal data class VideoEditPlan(val startMs:Long,val endMs:Long,val crop:VideoRect=VideoRect(),val mask:VideoRect?=null) {
    fun validate(durationMs:Long) {
        require(durationMs>0 && startMs>=0 && endMs<=durationMs && endMs-startMs>=250){"请保留至少 0.25 秒的视频"}
        require(crop.valid() && (mask==null || mask.valid())){"画面选区太小或无效，请重新框选"}
    }
    // Conservative reservation for output plus importing it as an independent library original.
    fun requiredBytes(sourceBytes:Long):Long = (sourceBytes.coerceAtLeast(1)*4).coerceAtMost(Codec.MAX_MEDIA_BYTES*2)+64L*1024*1024
}
