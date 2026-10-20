package dev.h516.shicang

/** BERT cased basic tokenization + greedy WordPiece; matches the pinned tokenizer's settings. */
internal class LocalWordPiece(vocab:List<String>) {
    private val ids=vocab.withIndex().associate{it.value to it.index.toLong()}
    fun encode(text:String,maxLength:Int=512):LongArray {
        require(maxLength in 2..512)
        val basic=mutableListOf<String>();val token=StringBuilder()
        fun flush(){if(token.isNotEmpty()){basic+=token.toString();token.setLength(0)}}
        val special=Regex("\\[(?:UNK|SEP|PAD|CLS|MASK)\\]")
        // Added special-token literals are atomic in the pinned HF tokenizer.
        val segments=mutableListOf<Pair<String,Boolean>>();var offset=0
        special.findAll(text).forEach{match->if(match.range.first>offset)segments+=text.substring(offset,match.range.first) to false;segments+=match.value to true;offset=match.range.last+1}
        if(offset<text.length)segments+=text.substring(offset) to false
        segments.forEach{(segment,isSpecial)->if(isSpecial){flush();basic+=segment}else segment.codePoints().forEach{cp->
            val type=Character.getType(cp)
            val chinese=cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2FA1F || cp in 0xF900..0xFAFF
            val punctuation=cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126 || type in listOf(Character.CONNECTOR_PUNCTUATION.toInt(),Character.DASH_PUNCTUATION.toInt(),Character.START_PUNCTUATION.toInt(),Character.END_PUNCTUATION.toInt(),Character.INITIAL_QUOTE_PUNCTUATION.toInt(),Character.FINAL_QUOTE_PUNCTUATION.toInt(),Character.OTHER_PUNCTUATION.toInt())
            when {
                cp==0 || cp==0xFFFD -> Unit
                Character.isWhitespace(cp) || type==Character.SPACE_SEPARATOR.toInt() -> flush()
                type==Character.CONTROL.toInt() || type==Character.FORMAT.toInt() || type==Character.PRIVATE_USE.toInt() || type==Character.SURROGATE.toInt() -> Unit
                chinese || punctuation -> {flush();basic+=String(Character.toChars(cp))}
                else -> token.appendCodePoint(cp)
            }
        };flush()}
        val output=mutableListOf(101L)
        for(word in basic) {
            if(output.size>=maxLength-1)break
            if(word in setOf("[UNK]","[SEP]","[PAD]","[CLS]","[MASK]")){output+=requireNotNull(ids[word]);continue}
            val points=word.codePoints().toArray();val pieces=mutableListOf<Long>();var start=0;var unknown=points.size>100
            while(start<points.size && !unknown){var end=points.size;var found:Long?=null
                while(end>start){val part=String(points,start,end-start);found=ids[(if(start>0)"##" else "")+part];if(found!=null)break;end--}
                if(found==null)unknown=true else {pieces+=found;start=end}
            }
            for(id in if(unknown)listOf(100L)else pieces){if(output.size>=maxLength-1)break;output+=id}
        };output+=102L;return output.toLongArray()
    }
}
