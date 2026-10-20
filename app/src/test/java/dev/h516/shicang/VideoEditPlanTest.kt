package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class VideoEditPlanTest {
    @Test fun fullFrameIsIdentity(){assertArrayEquals(floatArrayOf(-1f,1f,-1f,1f),VideoRect().cropCoordinates(),.0001f)}
    @Test fun topLeftCoordinatesConvertToNdcWithoutVerticalFlip(){assertArrayEquals(floatArrayOf(-.5f,.5f,-.6f,.8f),VideoRect(.25f,.1f,.75f,.8f).cropCoordinates(),.0001f)}
    @Test fun reverseDragNormalizesAndClamps(){assertEquals(VideoRect(0f,.2f,.9f,1f),VideoRect.between(.9f,2f,-.5f,.2f))}
    @Test fun tinyRectanglesAndNonFiniteValuesRejected(){assertFalse(VideoRect(0f,0f,.01f,1f).valid());assertFalse(VideoRect(Float.NaN,0f,1f,1f).valid());assertFalse(VideoRect(1f,0f,0f,1f).valid())}
    @Test fun timeSelectionMustStayInsideDuration(){listOf(VideoEditPlan(-1,1000),VideoEditPlan(0,1001),VideoEditPlan(800,900),VideoEditPlan(900,800)).forEach{assertTrue(runCatching{it.validate(1000)}.isFailure)}}
    @Test fun quarterSecondAndFullLengthAllowed(){VideoEditPlan(0,250).validate(250);VideoEditPlan(500,1000).validate(1000)}
    @Test fun maskAndCropValidatedSeparately(){assertTrue(runCatching{VideoEditPlan(0,1000,mask=VideoRect(0f,0f,0f,.5f)).validate(1000)}.isFailure)}
    @Test fun reservationIncludesNewFileAndImport(){assertTrue(VideoEditPlan(0,1000).requiredBytes(100*1024*1024)>200*1024*1024);assertTrue(VideoEditPlan(0,1000).requiredBytes(Codec.MAX_MEDIA_BYTES)>2*Codec.MAX_MEDIA_BYTES)}
}
