package dev.h516.shicang

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.window.Dialog

/** Product-owned controls. Orchard's private source and build artifacts are not redistributed. */
@Composable private fun GalleryAction(onClick:()->Unit,modifier:Modifier,enabled:Boolean,shape:Shape,
    colors:ButtonColors,padding:PaddingValues,content:@Composable RowScope.()->Unit) {
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val foreground=if(enabled)colors.contentColor else colors.disabledContentColor
    val background=if(enabled)colors.containerColor else colors.disabledContainerColor
    Row(modifier.heightIn(min=48.dp).clip(shape).background(background).alpha(if(pressed) .55f else 1f)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button,onClick=onClick).padding(padding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge){content()}
        }
    }
}

@Composable internal fun GalleryButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=CircleShape,contentPadding:PaddingValues=PaddingValues(horizontal=18.dp,vertical=10.dp),
    colors:ButtonColors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0066D6),contentColor=Color.White),content:@Composable RowScope.()->Unit) =
    GalleryAction(onClick,modifier,enabled,shape,colors,contentPadding,content)

@Composable internal fun GalleryTonalButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=CircleShape,contentPadding:PaddingValues=PaddingValues(horizontal=16.dp,vertical=10.dp),
    colors:ButtonColors=ButtonDefaults.filledTonalButtonColors(),content:@Composable RowScope.()->Unit) =
    GalleryAction(onClick,modifier,enabled,shape,colors,contentPadding,content)

@Composable internal fun GalleryTextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    shape:Shape=CircleShape,contentPadding:PaddingValues=PaddingValues(horizontal=12.dp,vertical=8.dp),
    colors:ButtonColors=ButtonDefaults.textButtonColors(),content:@Composable RowScope.()->Unit) =
    GalleryAction(onClick,modifier,enabled,shape,colors,contentPadding,content)

@Composable internal fun GallerySwitch(checked:Boolean,onCheckedChange:(Boolean)->Unit,enabled:Boolean=true,modifier:Modifier=Modifier) {
    val offset by animateDpAsState(if(checked)22.dp else 2.dp,label="switch")
    Box(modifier.size(width=56.dp,height=48.dp).alpha(if(enabled)1f else .4f)
        .toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange),contentAlignment=Alignment.Center) {
        Box(Modifier.size(width=51.dp,height=31.dp).clip(CircleShape).background(if(checked)Color(0xFF34C759)else MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.padding(top=2.dp).offset(x=offset).size(27.dp).background(Color.White,CircleShape))
        }
    }
}

@Composable internal fun GallerySearchField(value:String,change:(String)->Unit,label:String,modifier:Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    val focus=LocalFocusManager.current
    Row(modifier.fillMaxWidth().heightIn(min=52.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceVariant).padding(start=14.dp,end=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FrameGlyph("搜索",c.onSurfaceVariant)
        BasicTextField(value,change,Modifier.weight(1f).padding(vertical=12.dp).semantics{contentDescription=label},singleLine=true,
            cursorBrush=SolidColor(c.primary),textStyle=MaterialTheme.typography.bodyLarge.copy(color=c.onSurface),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={focus.clearFocus()}),
            decorationBox={field->Box{if(value.isEmpty())Text(label,style=MaterialTheme.typography.bodyLarge,color=c.onSurfaceVariant);field()}})
        if(value.isNotEmpty())GalleryTextButton(onClick={change("")},modifier=Modifier.semantics{contentDescription="清除搜索"},contentPadding=PaddingValues(horizontal=12.dp)){Text("×")}
    }
}

@Composable internal fun GalleryTextField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,
    label:(@Composable ()->Unit)?=null,enabled:Boolean=true,singleLine:Boolean=false,minLines:Int=1,maxLines:Int=Int.MAX_VALUE,
    keyboardOptions:androidx.compose.foundation.text.KeyboardOptions=androidx.compose.foundation.text.KeyboardOptions.Default) {
    val c=MaterialTheme.colorScheme
    BasicTextField(value,onValueChange,modifier.clip(RoundedCornerShape(14.dp)).background(c.surface),
        enabled=enabled,singleLine=singleLine,minLines=minLines,maxLines=maxLines,keyboardOptions=keyboardOptions,
        cursorBrush=SolidColor(c.primary),textStyle=MaterialTheme.typography.bodyLarge.copy(color=c.onSurface),
        decorationBox={field->Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            label?.let{CompositionLocalProvider(LocalContentColor provides c.onSurfaceVariant){ProvideTextStyle(MaterialTheme.typography.bodySmall){it()}}}
            field()
        }})
}

@Composable internal fun GallerySegments(options:List<Pair<String,String>>,selected:String,select:(String)->Unit,modifier:Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    val large=options.sumOf{it.second.length}*14*LocalDensity.current.fontScale+options.size*24>LocalConfiguration.current.screenWidthDp-46
    val item:@Composable (Pair<String,String>)->Unit={ (key,label)->
        Box(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("index-$key").clip(RoundedCornerShape(10.dp))
            .background(if(selected==key)c.surface else Color.Transparent)
            .selectable(selected==key,onClick={select(key)},role=Role.Tab,interactionSource=remember{MutableInteractionSource()},indication=null)
            .padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
            Text(label,style=MaterialTheme.typography.labelLarge,color=if(selected==key)c.onSurface else c.onSurfaceVariant)
        }
    }
    Surface(modifier.selectableGroup(),color=c.surfaceVariant,shape=RoundedCornerShape(12.dp)) {
        if(large) Column(Modifier.fillMaxWidth().padding(3.dp)){options.forEach{option->Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){item(option)}}}
        else Row(Modifier.fillMaxWidth().padding(3.dp),horizontalArrangement=Arrangement.SpaceEvenly){options.forEach{option->Box(Modifier.weight(1f),contentAlignment=Alignment.Center){item(option)}}}
    }
}

@Composable internal fun GalleryTabBar(route:String,select:(String)->Unit) {
    val c=MaterialTheme.colorScheme
    val immersive=route=="frame"
    val foreground=if(immersive) Color.White else c.primary
    val secondary=if(immersive) Color.White.copy(alpha=.65f) else c.onSurfaceVariant
    val shape=RoundedCornerShape(32.dp)
    Surface(Modifier.navigationBarsPadding().padding(horizontal=24.dp,vertical=8.dp)
        .border(.5.dp,if(immersive) Color.White.copy(alpha=.18f) else c.outlineVariant.copy(alpha=.5f),shape),
        shape=shape,color=if(immersive) Color(0xF2222224) else c.surface,shadowElevation=1.dp) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf("library" to "档案","frame" to "发现").forEach{(destination,label)->
                Column(Modifier.weight(1f).heightIn(min=54.dp).clip(RoundedCornerShape(28.dp))
                    .background(if(route==destination) {if(immersive) Color.White.copy(alpha=.14f) else c.surfaceVariant.copy(alpha=.6f)} else Color.Transparent)
                    .testTag("nav-$destination").semantics{contentDescription="切换到$label"}
                    .selectable(route==destination,onClick={select(destination)},role=Role.Tab,interactionSource=remember{MutableInteractionSource()},indication=null)
                    .padding(horizontal=8.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    FrameGlyph(if(destination=="settings")"设置" else label,if(route==destination)foreground else secondary)
                    Text(label,style=MaterialTheme.typography.labelSmall,color=if(route==destination)foreground else secondary)
                }
            }
        }
    }
}

@Composable internal fun GalleryAlertDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,
    text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:(@Composable ()->Unit)?=null) {
    val maximum=LocalConfiguration.current.screenHeightDp.dp*.85f
    Dialog(onDismissRequest) {
        Surface(Modifier.widthIn(max=360.dp).fillMaxWidth(),shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.heightIn(max=maximum),horizontalAlignment=Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=22.dp,bottom=12.dp),contentAlignment=Alignment.Center) {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(textAlign=TextAlign.Center)){title()}
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=16.dp),contentAlignment=Alignment.Center) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(textAlign=TextAlign.Center)){text()}
                    }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){confirmButton()}
                dismissButton?.let{HorizontalDivider();Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){it()}}
            }
        }
    }
}
