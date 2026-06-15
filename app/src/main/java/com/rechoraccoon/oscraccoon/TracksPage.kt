package com.rechoraccoon.oscraccoon

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun TracksPage(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(AppPreferences.loadPlaylists(context)) }
    var selectedPlaylistId by remember { mutableStateOf("all") }
    var gridColumns by remember { mutableStateOf(3) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    // Drag state
    var draggingTrack by remember { mutableStateOf<LocalTrack?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val playlistBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var hoveredPlaylistId by remember { mutableStateOf<String?>(null) }

    val allTracks = LocalMediaState.tracks.sortedBy { it.title }

    val displayedTracks = remember(selectedPlaylistId, playlists, LocalMediaState.tracks.size) {
        if (selectedPlaylistId == "all") {
            LocalMediaState.tracks.sortedBy { it.title }
        } else {
            val pl = playlists.find { it.id == selectedPlaylistId }
            pl?.trackUris?.mapNotNull { uri -> LocalMediaState.tracks.find { it.uri.toString() == uri } } ?: emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize().drawBehind { drawCheckerboard() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header (not overlaid) ──────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().background(BrownMid.copy(alpha = 0.9f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RaccoonButton(text = "← Back", small = true, onClick = onDismiss)
                    Text("Tracks & Playlists", color = GreenPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Text("Drag a track onto a playlist to add it", color = GreenPrimary.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            // ── Main content ───────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                // LEFT: Track list
                Column(modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(BrownMid.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(1.dp, GreenPrimary, RoundedCornerShape(10.dp))
                    .padding(10.dp)) {

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        val label = if (selectedPlaylistId == "all") "All Tracks (${allTracks.size})" else "${playlists.find { it.id == selectedPlaylistId }?.name ?: "Tracks"} (${displayedTracks.size})"
                        Text(label, color = GreenPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        if (selectedPlaylistId != "all") {
                            RaccoonButton(text = "← All", small = true, onClick = { selectedPlaylistId = "all" })
                        }
                    }

                    if (LocalMediaState.tracks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tracks loaded.\nGo back and pick a folder.", color = GreenPrimary.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(displayedTracks) { index, track ->
                                val isCurrent = LocalMediaState.currentTrack?.uri == track.uri
                                var rowPos by remember { mutableStateOf(Offset.Zero) }
                                val isDraggingThis = draggingTrack == track

                                val scale by animateFloatAsState(if (isDraggingThis) 0.95f else 1f, spring(0.6f, 400f), label = "ts")

                                Row(
                                    modifier = Modifier.fillMaxWidth().scale(scale)
                                        .border(1.dp, if (isCurrent) GreenPrimary else GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .background(if (isCurrent) GreenPrimary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(6.dp))
                                        .onGloballyPositioned { coords -> rowPos = coords.positionInRoot() }
                                        .pointerInput(track) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    draggingTrack = track
                                                    dragPosition = rowPos + offset
                                                },
                                                onDrag = { _, dragAmount ->
                                                    dragPosition += dragAmount
                                                    hoveredPlaylistId = playlistBounds.entries.firstOrNull { (_, rect) ->
                                                        rect.contains(dragPosition)
                                                    }?.key
                                                },
                                                onDragEnd = {
                                                    val targetId = hoveredPlaylistId
                                                    val t = draggingTrack
                                                    if (targetId != null && targetId != "all" && t != null) {
                                                        val updated = playlists.map { pl ->
                                                            if (pl.id == targetId && !pl.trackUris.contains(t.uri.toString()))
                                                                pl.copy(trackUris = pl.trackUris + t.uri.toString())
                                                            else pl
                                                        }
                                                        playlists = updated
                                                        AppPreferences.savePlaylists(context, updated)
                                                    }
                                                    draggingTrack = null
                                                    hoveredPlaylistId = null
                                                },
                                                onDragCancel = { draggingTrack = null; hoveredPlaylistId = null }
                                            )
                                        }
                                        .clickable {
                                            val queueIdx = LocalMediaState.playQueue.indexOfFirst { it.uri == track.uri }
                                            if (queueIdx >= 0) LocalMediaState.playTrack(queueIdx)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(if (isCurrent) "▶" else "${index + 1}.", color = if (isCurrent) GreenPrimary else GreenPrimary.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = GreenPrimary.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { LocalMediaState.addToQueue(track) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                    }
                                    if (selectedPlaylistId != "all") {
                                        IconButton(onClick = {
                                            val updated = playlists.map { pl ->
                                                if (pl.id == selectedPlaylistId) pl.copy(trackUris = pl.trackUris.filter { it != track.uri.toString() }) else pl
                                            }
                                            playlists = updated; AppPreferences.savePlaylists(context, updated)
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Remove, contentDescription = "Remove", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // RIGHT: Playlists
                Column(modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(BrownMid.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(1.dp, GreenPrimary, RoundedCornerShape(10.dp))
                    .padding(10.dp)) {

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Playlists", color = GreenPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        RaccoonButton(text = "+ New", small = true, onClick = { showCreatePlaylist = true })
                    }

                    // All Tracks card — full width at top
                    val allHovered = hoveredPlaylistId == "all_visual"
                    val allSelected = selectedPlaylistId == "all"
                    val allScale by animateFloatAsState(if (allHovered) 1.03f else 1f, spring(0.5f, 500f), label = "as")
                    Row(modifier = Modifier.fillMaxWidth().scale(allScale).padding(bottom = 8.dp)
                        .border(2.dp, if (allSelected) GreenPrimary else GreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(if (allSelected) GreenPrimary.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedPlaylistId = "all" }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎵", fontSize = 20.sp)
                        Column {
                            Text("All Tracks", color = GreenPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${allTracks.size} tracks • Alphabetical", color = GreenPrimary.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Playlist grid
                    if (playlists.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No playlists yet.\nDrag tracks here after\ncreating a playlist.", color = GreenPrimary.copy(alpha = 0.4f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(gridColumns.coerceIn(1, 5)), modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(playlists, key = { _, pl -> pl.id }) { _, playlist ->
                                var isRenaming by remember { mutableStateOf(false) }
                                var renameText by remember(playlist.name) { mutableStateOf(playlist.name) }
                                val isHov = hoveredPlaylistId == playlist.id
                                val isSel = selectedPlaylistId == playlist.id
                                val cardScale by animateFloatAsState(when { isHov -> 1.06f; isSel -> 1.02f; else -> 1f }, spring(0.5f, 500f), label = "cs")

                                Column(modifier = Modifier.scale(cardScale)
                                    .border(2.dp, if (isHov) GreenPrimary else if (isSel) GreenPrimary else GreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .background(if (isHov) GreenPrimary.copy(alpha = 0.15f) else if (isSel) GreenPrimary.copy(alpha = 0.1f) else BrownMid.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        playlistBounds[playlist.id] = androidx.compose.ui.geometry.Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                                    }
                                    .clickable { if (!isRenaming) selectedPlaylistId = playlist.id }
                                    .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {

                                    // Cover box
                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .background(BrownDark, RoundedCornerShape(6.dp))
                                        .border(1.dp, GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Text("🎵", fontSize = 22.sp)
                                    }

                                    if (isRenaming) {
                                        BasicTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true,
                                            textStyle = TextStyle(color = GreenPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                                            cursorBrush = SolidColor(GreenPrimary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = {
                                                val updated = playlists.map { if (it.id == playlist.id) it.copy(name = renameText) else it }
                                                playlists = updated; AppPreferences.savePlaylists(context, updated); isRenaming = false
                                            }), modifier = Modifier.fillMaxWidth())
                                    } else {
                                        Text(playlist.name, color = GreenPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                    }
                                    Text("${playlist.trackUris.size} tracks", color = GreenPrimary.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (isRenaming) {
                                            IconButton(onClick = {
                                                val updated = playlists.map { if (it.id == playlist.id) it.copy(name = renameText) else it }
                                                playlists = updated; AppPreferences.savePlaylists(context, updated); isRenaming = false
                                            }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Check, contentDescription = "Done", tint = GreenPrimary, modifier = Modifier.size(14.dp)) }
                                        } else {
                                            IconButton(onClick = { isRenaming = true }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Edit, contentDescription = "Rename", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp)) }
                                            IconButton(onClick = {
                                                val updated = playlists.filter { it.id != playlist.id }
                                                playlists = updated; AppPreferences.savePlaylists(context, updated)
                                                if (selectedPlaylistId == playlist.id) selectedPlaylistId = "all"
                                            }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Grid size slider
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Size:", color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Slider(value = gridColumns.toFloat(), onValueChange = { gridColumns = it.toInt() }, valueRange = 1f..5f, steps = 3,
                            modifier = Modifier.weight(1f).height(20.dp),
                            colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary, inactiveTrackColor = GreenPrimary.copy(alpha = 0.3f)))
                    }
                }
            }
        }

        // Drag ghost — full-size overlay Box so it can appear anywhere
        if (draggingTrack != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(99f)) {
                Box(modifier = Modifier.offset { IntOffset(dragPosition.x.roundToInt() - 20, dragPosition.y.roundToInt() - 20) }
                    .widthIn(max = 280.dp)
                    .background(BrownMid.copy(alpha = 0.95f), RoundedCornerShape(6.dp))
                    .border(1.dp, GreenPrimary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column {
                        Text(draggingTrack!!.title, color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(draggingTrack!!.artist, color = GreenPrimary.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                val newPl = VirtualPlaylist(id = UUID.randomUUID().toString(), name = name)
                val updated = playlists + newPl
                playlists = updated; AppPreferences.savePlaylists(context, updated)
                showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false }
        )
    }
}

@Composable
fun CreatePlaylistDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("New Playlist") }
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.width(280.dp).clip(RoundedCornerShape(12.dp)).drawBehind { drawCheckerboard() }.border(2.dp, GreenPrimary, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Create Playlist", color = GreenPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                HorizontalDivider(color = GreenPrimary.copy(alpha = 0.3f))
                Text("Name:", color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = TextStyle(color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(GreenPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GreenPrimary, RoundedCornerShape(6.dp)).padding(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RaccoonButton(text = "Create", highlighted = true, onClick = { if (name.isNotBlank()) onConfirm(name.trim()) })
                    RaccoonButton(text = "Cancel", onClick = onDismiss)
                }
            }
        }
    }
}
