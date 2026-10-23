package dev.h516.shicang

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Name-only archives are complete entries, never missing-image error placeholders. */
@Composable internal fun ArchiveNameplate(name:String,modifier:Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(c.surfaceVariant,c.secondaryContainer))),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(10.dp).border(1.dp,c.onSurfaceVariant.copy(alpha=.12f),RoundedCornerShape(9.dp)))
        Text(name.take(1).ifBlank{"留"},style=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.Normal),color=c.onSurfaceVariant)
    }
}

@Composable internal fun ActiveArchiveFilter(label:String,description:String,remove:()->Unit) {
    InputChip(selected=true,onClick=remove,modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription=description},
        label={Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)},trailingIcon={Text("×",fontSize=18.sp)},
        colors=InputChipDefaults.inputChipColors(selectedContainerColor=MaterialTheme.colorScheme.secondaryContainer,selectedLabelColor=MaterialTheme.colorScheme.onSecondaryContainer))
}

@Composable internal fun ArchiveShelfCard(a:Archive,library:Library,model:AppModel,open:()->Unit,view:()->Unit) {
    ArchiveStoryCard(a,library,model,compact=true,open=open,view=view)
}

@Composable internal fun ArchiveShelfRow(a:Archive,library:Library,model:AppModel,open:()->Unit,view:()->Unit) {
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).clickable(onClickLabel="打开${a.name}的档案",onClick=open).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        if(a.photos.isNotEmpty()) IdentityCover(a,model,Modifier.size(72.dp).semantics{contentDescription="${a.name}的封面"})
        Column(Modifier.weight(1f)) {
            Text(a.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            val caption=library.archiveSummary(a)
            if(caption.isNotBlank()) Text(caption,Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        FrameGlyph("展开",c.outline)
    }
}
