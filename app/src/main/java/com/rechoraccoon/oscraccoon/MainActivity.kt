package com.rechoraccoon.oscraccoon

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

val GreenPrimary = Color(0xFF00FF07)
val BrownDark    = Color(0xFF5C3317)
val BrownLight   = Color(0xFF7A4A25)
val BrownMid     = Color(0xFF6B3D1E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OSCRaccoonApp() }
    }
}

fun DrawScope.drawCheckerboard(
    colorA: Color = BrownDark,
    colorB: Color = BrownLight,
    cellSize: Float = 24f
) {
    val cols = (size.width / cellSize).toInt() + 1
    val rows = (size.height / cellSize).toInt() + 1
    for (row in 0..rows) {
        for (col in 0..cols) {
            val color = if ((row + col) % 2 == 0) colorA else colorB
            drawRect(color = color, topLeft = Offset(col * cellSize, row * cellSize), size = Size(cellSize, cellSize))
        }
    }
}

@Composable
fun OSCRaccoonApp() {
    val context = LocalContext.current

    // Single unified message template - cycling codes go wherever user wants
    var messageTemplate by remember { mutableStateOf("🎵 {song}\nby {artist} | {duration}\n{progress}\n{cycling}") }
    var cyclingMessages by remember { mutableStateOf(listOf<String>()) }
    var cycleInterval by remember { mutableStateOf(5) }
    var isRunning by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf(NowPlaying()) }
    var previewCycleIndex by remember { mutableStateOf(0) }

    // Collect media updates
    LaunchedEffect(Unit) {
        MediaListenerService.nowPlaying.collectLatest { nowPlaying = it }
    }

    // Preview cycle ticker
    LaunchedEffect(cyclingMessages, cycleInterval) {
        while (true) {
            delay(cycleInterval * 1000L)
            if (cyclingMessages.isNotEmpty())
                previewCycleIndex = (previewCycleIndex + 1) % cyclingMessages.size
        }
    }

    // Live preview
    val currentCycling = if (cyclingMessages.isNotEmpty()) cyclingMessages[previewCycleIndex] else ""
    val livePreview = OscForegroundService.formatTemplate(messageTemplate, nowPlaying, currentCycling)

    // Push live template/cycling updates to running service
    LaunchedEffect(messageTemplate, cyclingMessages, cycleInterval) {
        if (isRunning) {
            val svc = Intent(context, OscForegroundService::class.java).apply {
                action = OscForegroundService.ACTION_UPDATE
                putExtra(OscForegroundService.EXTRA_MAIN_TEMPLATE, messageTemplate)
                putStringArrayListExtra(OscForegroundService.EXTRA_CYCLING_MESSAGES, ArrayList(cyclingMessages))
                putExtra(OscForegroundService.EXTRA_CYCLE_INTERVAL, cycleInterval)
            }
            context.startForegroundService(svc)
        }
    }

    Box(modifier = Modifier.fillMaxSize().drawBehind { drawCheckerboard() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                isRunning = isRunning,
                onAboutClick = { showAbout = true },
                onStartStop = {
                    if (isRunning) {
                        context.stopService(Intent(context, OscForegroundService::class.java))
                        isRunning = false
                    } else {
                        val svc = Intent(context, OscForegroundService::class.java).apply {
                            action = OscForegroundService.ACTION_START
                            putExtra(OscForegroundService.EXTRA_MAIN_TEMPLATE, messageTemplate)
                            putStringArrayListExtra(OscForegroundService.EXTRA_CYCLING_MESSAGES, ArrayList(cyclingMessages))
                            putExtra(OscForegroundService.EXTRA_CYCLE_INTERVAL, cycleInterval)
                        }
                        context.startForegroundService(svc)
                        isRunning = true
                    }
                },
                onClearChatbox = { OscSender.clearChatbox() }
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LEFT — unified template
                LeftPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    messageTemplate = messageTemplate,
                    onTemplateChange = { messageTemplate = it },
                    nowPlaying = nowPlaying,
                    livePreview = livePreview
                )
                // RIGHT — cycling messages list
                RightPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    cyclingMessages = cyclingMessages,
                    cycleInterval = cycleInterval,
                    onMessagesChange = { cyclingMessages = it },
                    onIntervalChange = { cycleInterval = it }
                )
            }
        }
        if (showAbout) AboutDialog { showAbout = false }
    }
}

@Composable
fun HeaderBar(isRunning: Boolean, onAboutClick: () -> Unit, onStartStop: () -> Unit, onClearChatbox: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(BrownMid.copy(alpha = 0.85f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "OSCRaccoon by RechoRaccoon",
            color = GreenPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RaccoonButton(text = "Clear Chatbox", onClick = onClearChatbox, small = true)
            RaccoonButton(text = if (isRunning) "■ Stop" else "▶ Start", onClick = onStartStop, highlighted = !isRunning)
            RaccoonButton(text = "About", onClick = onAboutClick, small = true)
        }
    }
}

@Composable
fun LeftPanel(modifier: Modifier, messageTemplate: String, onTemplateChange: (String) -> Unit, nowPlaying: NowPlaying, livePreview: String) {
    PanelCard(modifier = modifier, title = "Message Template") {
        Text(
            "Placeholders:  {song}  {artist}  {duration}  {progress}  {cycling}",
            color = GreenPrimary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        RaccoonTextArea(
            value = messageTemplate,
            onValueChange = onTemplateChange,
            label = "Message template",
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )
        Spacer(Modifier.height(12.dp))
        SectionLabel("Now Playing")
        NowPlayingCard(nowPlaying)
        Spacer(Modifier.height(12.dp))
        SectionLabel("Live Chatbox Preview")
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .border(1.dp, GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Text(livePreview.ifEmpty { "(empty)" }, color = GreenPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
        }
    }
}

@Composable
fun RightPanel(modifier: Modifier, cyclingMessages: List<String>, cycleInterval: Int, onMessagesChange: (List<String>) -> Unit, onIntervalChange: (Int) -> Unit) {
    var newMessage by remember { mutableStateOf("") }
    PanelCard(modifier = modifier, title = "Cycling Messages  ({cycling})") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Cycle every:", color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Slider(
                value = cycleInterval.toFloat(),
                onValueChange = { onIntervalChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary, inactiveTrackColor = GreenPrimary.copy(alpha = 0.3f))
            )
            Text("${cycleInterval}s", color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            RaccoonTextField(value = newMessage, onValueChange = { newMessage = it }, placeholder = "New cycling message...", modifier = Modifier.weight(1f))
            RaccoonButton(text = "+ Add", onClick = {
                if (newMessage.isNotBlank()) { onMessagesChange(cyclingMessages + newMessage.trim()); newMessage = "" }
            }, small = true)
        }
        Spacer(Modifier.height(8.dp))
        if (cyclingMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No cycling messages yet.\nAdd one above!\n\nUse {cycling} in your\ntemplate to place them.", color = GreenPrimary.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(cyclingMessages) { index, message ->
                    CyclingMessageRow(
                        message = message, index = index,
                        onDelete = { onMessagesChange(cyclingMessages.toMutableList().also { it.removeAt(index) }) },
                        onMoveUp = {
                            if (index > 0) {
                                val list = cyclingMessages.toMutableList()
                                val tmp = list[index]; list[index] = list[index-1]; list[index-1] = tmp
                                onMessagesChange(list)
                            }
                        },
                        onMoveDown = {
                            if (index < cyclingMessages.size - 1) {
                                val list = cyclingMessages.toMutableList()
                                val tmp = list[index]; list[index] = list[index+1]; list[index+1] = tmp
                                onMessagesChange(list)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CyclingMessageRow(message: String, index: Int, onDelete: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, GreenPrimary.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("${index+1}.", color = GreenPrimary.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(20.dp))
        Text(message, color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = GreenPrimary.copy(alpha = 0.7f)) }
        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = GreenPrimary.copy(alpha = 0.7f)) }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, contentDescription = "Delete", tint = GreenPrimary.copy(alpha = 0.7f)) }
    }
}

@Composable
fun NowPlayingCard(nowPlaying: NowPlaying) {
    val posMin = (nowPlaying.positionMs / 1000) / 60
    val posSec = (nowPlaying.positionMs / 1000) % 60
    val durMin = (nowPlaying.durationMs / 1000) / 60
    val durSec = (nowPlaying.durationMs / 1000) % 60
    Box(modifier = Modifier.fillMaxWidth().border(1.dp, GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp)).padding(10.dp)) {
        Column {
            Text(if (nowPlaying.title.isNotEmpty()) nowPlaying.title else "Nothing Playing", color = GreenPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (nowPlaying.artist.isNotEmpty()) Text(nowPlaying.artist, color = GreenPrimary.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            if (nowPlaying.durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                val progress = (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = GreenPrimary, trackColor = GreenPrimary.copy(alpha = 0.2f))
                Spacer(Modifier.height(2.dp))
                Text("%d:%02d / %d:%02d".format(posMin, posSec, durMin, durSec), color = GreenPrimary.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember {
        try { BitmapFactory.decodeStream(context.assets.open("recho_icon.png"))?.asImageBitmap() }
        catch (e: Exception) { null }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.width(280.dp).drawBehind { drawCheckerboard() }.border(2.dp, GreenPrimary, RoundedCornerShape(12.dp)).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = "Recho Raccoon", modifier = Modifier.size(80.dp).border(2.dp, GreenPrimary, RoundedCornerShape(40.dp)))
                    Spacer(Modifier.height(10.dp))
                }
                Text("OSCRaccoon", color = GreenPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("by RechoRaccoon", color = GreenPrimary.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("The One And Only", color = GreenPrimary.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(16.dp))
                RaccoonButton(text = "Recho's Socials", onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://guns.lol/rechoraccoon")))
                })
                Spacer(Modifier.height(8.dp))
                RaccoonButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun PanelCard(modifier: Modifier, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.background(BrownMid.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).border(1.dp, GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(title, color = GreenPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = GreenPrimary.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun RaccoonButton(text: String, onClick: () -> Unit, small: Boolean = false, highlighted: Boolean = false) {
    val bg = if (highlighted) GreenPrimary else Color.Transparent
    val fg = if (highlighted) BrownDark else GreenPrimary
    Box(
        modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).border(1.dp, GreenPrimary, RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = if (small) 10.dp else 16.dp, vertical = if (small) 5.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = if (small) 11.sp else 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun RaccoonTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(GreenPrimary),
        modifier = modifier.border(1.dp, GreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = GreenPrimary.copy(alpha = 0.35f), fontSize = 12.sp, fontFamily = FontFamily.Monospace); inner() }
    )
}

@Composable
fun RaccoonTextArea(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value, onValueChange = onValueChange,
        textStyle = TextStyle(color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
        cursorBrush = SolidColor(GreenPrimary),
        modifier = modifier.border(1.dp, GreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(10.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(label, color = GreenPrimary.copy(alpha = 0.35f), fontSize = 12.sp, fontFamily = FontFamily.Monospace); inner() }
    )
}
