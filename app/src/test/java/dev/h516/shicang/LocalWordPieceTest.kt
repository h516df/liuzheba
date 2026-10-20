package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class LocalWordPieceTest {
    private val tokenizer=LocalWordPiece(requireNotNull(javaClass.getResourceAsStream("/bge-vocab.txt")).bufferedReader().readLines())
    @Test fun matchesPinnedHuggingFaceTokenizerGoldens(){
        val cases=mapOf(
            "中文，Hello World!" to longArrayOf(101,704,3152,8024,100,100,106,102),
            "Café\tABC\n你好😊" to longArrayOf(101,100,100,872,1962,100,102),
            "[CLS]你好[SEP]" to longArrayOf(101,101,872,1962,102,102),
            "𠀀々—测试" to longArrayOf(101,100,513,100,3844,6407,102),
            "a\u0000b\u200bc" to longArrayOf(101,8425,102),
            "a\ue000b" to longArrayOf(101,9386,102),
            "a\u0378b" to longArrayOf(101,100,102),
            "a\u00a0b" to longArrayOf(101,143,144,102),
            "[MASK]中文[UNK]" to longArrayOf(101,103,704,3152,100,102))
        cases.forEach{(text,expected)->assertArrayEquals(text,expected,tokenizer.encode(text))}
    }
    @Test fun truncationAlwaysPreservesSeparators(){val result=tokenizer.encode("中文".repeat(1000));assertEquals(512,result.size);assertEquals(101L,result.first());assertEquals(102L,result.last())}
    @Test fun longUnknownWordsAndEmptyInputAreBounded(){assertArrayEquals(longArrayOf(101,100,102),tokenizer.encode("z".repeat(101)));assertArrayEquals(longArrayOf(101,102),tokenizer.encode(""));assertArrayEquals(longArrayOf(101,102),tokenizer.encode("中文",2))}
}
