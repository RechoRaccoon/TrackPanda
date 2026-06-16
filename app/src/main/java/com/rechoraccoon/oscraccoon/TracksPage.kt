package com.rechoraccoon.oscraccoon

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun TracksPage(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(AppPreferences.loadPlaylists(context)) }
    var selectedPlaylistId by remember { mutableStateOf("all") }
    var gridColumns by remember { mutableStateOf(3) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    // Drag-to-playlist state (only active in "all" view)
    var draggingTrack by remember { mutableStateOf<LocalTrack?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val playlistBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var hoveredPlaylistId by remember { mutableStateOf<String?>(null) }

    // Drag-to-reorder state (only active in playlist view)
    var reorderDraggingIndex by remember { mutableStateOf<Int?>(null) }
    var reorderTotalDragY by remember { mutableStateOf(0f) }
    var reorderDragPosition by remember { mutableStateOf(Offset.Zero) }

    // Queue add visual feedback
    var recentlyQueuedUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recentlyQueuedUri) {
        if (recentlyQueuedUri != null) { delay(1500L); recentlyQueuedUri = null }
    }

    val allTracks = LocalMediaState.tracks.sortedBy { it.title }
    val displayedTracks = remember(selectedPlaylistId, playlists, LocalMediaState.tracks.size) {
        if (selectedPlaylistId == "all") {
            LocalMediaState.tracks.sortedBy { it.title }
        } else {
            val pl = playlists.find { it.id == selectedPlaylistId }
            pl?.trackUris?.mapNotNull { uri -> LocalMediaState.tracks.find { it.uri.toString() == uri } } ?: emptyList()
        }
    }

    val trackListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().drawBehind { drawCheckerboard() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().background(BrownMid.copy(alpha = 0.9f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RaccoonButton(text = "← Back", small = true, onClick = onDismiss)
                    Text("Tracks & Playlists", color = GreenPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                val hint = if (selectedPlaylistId == "all") "Drag a track onto a playlist to add it"
                           else "Drag ≡ handle to reorder tracks in this playlist"
                Text(hint, color = GreenPrimary.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            // ── Main content ──────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── LEFT: Track list ──────────────────────────────────────────
                Column(modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(BrownMid.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(1.dp, GreenPrimary, RoundedCornerShape(10.dp))
                    .padding(10.dp)) {

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        val label = if (selectedPlaylistId == "all") "All Tracks (${allTracks.size})"
                                    else "${playlists.find { it.id == selectedPlaylistId }?.name ?: "Tracks"} (${displayedTracks.size})"
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
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // Green pill scrollbar on the left
                            GreenScrollbar(listState = trackListState, modifier = Modifier.fillMaxHeight().padding(end = 5.dp, top = 2.dp, bottom = 2.dp))

                            // Track list
                            LazyColumn(state = trackListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(displayedTracks, key = { _, track -> track.uri.toString() }) { index, track ->
                                    val isCurrent = LocalMediaState.currentTrack?.uri == track.uri
                                    val isJustQueued = recentlyQueuedUri == track.uri.toString()
                                    val isDraggingThis = draggingTrack == track || reorderDraggingIndex == index
                                    var rowPos by remember { mutableStateOf(Offset.Zero) }
                                    val rowScale by animateFloatAsState(if (isDraggingThis) 0.95f else 1f, spring(0.6f, 400f), label = "ts")

                                    Row(
                                        modifier = Modifier.fillMaxWidth().scale(rowScale)
                                            .border(1.dp, if (isCurrent) GreenPrimary else GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .background(if (isCurrent) GreenPrimary.copy(alpha = 0.1f) else if (reorderDraggingIndex == index) GreenPrimary.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(6.dp))
                                            .onGloballyPositioned { coords -> rowPos = coords.positionInRoot() }
                                            .then(
                                                // Drag-to-playlist only when viewing all tracks
                                                if (selectedPlaylistId == "all") Modifier.pointerInput(track) {
                                                    detectDragGestures(
                                                        onDragStart = { offset -> draggingTrack = track; dragPosition = rowPos + offset },
                                                        onDrag = { _, dragAmount ->
                                                            dragPosition += dragAmount
                                                            hoveredPlaylistId = playlistBounds.entries.firstOrNull { (_, rect) -> rect.contains(dragPosition) }?.key
                                                        },
                                                        onDragEnd = {
                                                            val targetId = hoveredPlaylistId; val t = draggingTrack
                                                            if (targetId != null && targetId != "all" && t != null) {
                                                                val updated = playlists.map { pl ->
                                                                    if (pl.id == targetId && !pl.trackUris.contains(t.uri.toString())) pl.copy(trackUris = pl.trackUris + t.uri.toString()) else pl
                                                                }
                                                                playlists = updated; AppPreferences.savePlaylists(context, updated)
                                                            }
                                                            draggingTrack = null; hoveredPlaylistId = null
                                                        },
                                                        onDragCancel = { draggingTrack = null; hoveredPlaylistId = null }
                                                    )
                                                } else Modifier
                                            )
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
                                        // Queue button with ✓ feedback
                                        IconButton(onClick = { LocalMediaState.addToQueue(track); recentlyQueuedUri = track.uri.toString() }, modifier = Modifier.size(28.dp)) {
                                            Icon(
                                                if (isJustQueued) Icons.Default.Check else Icons.Default.QueueMusic,
                                                contentDescription = if (isJustQueued) "Added!" else "Add to Queue",
                                                tint = if (isJustQueued) GreenPrimary else GreenPrimary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (selectedPlaylistId != "all") {
                                            // Remove from playlist
                                            IconButton(onClick = {
                                                val updated = playlists.map { pl ->
                                                    if (pl.id == selectedPlaylistId) pl.copy(trackUris = pl.trackUris.filter { it != track.uri.toString() }) else pl
                                                }
                                                playlists = updated; AppPreferences.savePlaylists(context, updated)
                                            }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Remove, contentDescription = "Remove", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                            }
                                            // Drag handle for reordering
                                            Box(modifier = Modifier.size(28.dp)
                                                .pointerInput(index, displayedTracks.size) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            reorderDraggingIndex = index
                                                            reorderTotalDragY = 0f
                                                            reorderDragPosition = rowPos + offset
                                                        },
                                                        onDrag = { _, dragAmount ->
                                                            reorderTotalDragY += dragAmount.y
                                                            reorderDragPosition += dragAmount
                                                        },
                                                        onDragEnd = {
                                                            val from = reorderDraggingIndex ?: run { reorderDraggingIndex = null; return@detectDragGestures }
                                                            val estHeightPx = 52.dp.toPx()
                                                            val delta = (reorderTotalDragY / estHeightPx).roundToInt()
                                                            val to = (from + delta).coerceIn(0, displayedTracks.size - 1)
                                                            if (from != to) {
                                                                val pl = playlists.find { it.id == selectedPlaylistId }
                                                                if (pl != null) {
                                                                    val fromUri = displayedTracks.getOrNull(from)?.uri?.toString()
                                                                    val toUri = displayedTracks.getOrNull(to)?.uri?.toString()
                                                                    if (fromUri != null && toUri != null) {
                                                                        val uris = pl.trackUris.toMutableList()
                                                                        val fi = uris.indexOf(fromUri); val ti = uris.indexOf(toUri)
                                                                        if (fi >= 0 && ti >= 0) {
                                                                            val removed = uris.removeAt(fi); uris.add(ti, removed)
                                                                            val updated = playlists.map { if (it.id == selectedPlaylistId) it.copy(trackUris = uris) else it }
                                                                            playlists = updated; AppPreferences.savePlaylists(context, updated)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            reorderDraggingIndex = null
                                                        },
                                                        onDragCancel = { reorderDraggingIndex = null }
                                                    )
                                                },
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.DragHandle, contentDescription = "Reorder", tint = GreenPrimary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── RIGHT: Playlists ──────────────────────────────────────────
                Column(modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(BrownMid.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(1.dp, GreenPrimary, RoundedCornerShape(10.dp))
                    .padding(10.dp)) {

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Playlists", color = GreenPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        RaccoonButton(text = "+ New", small = true, onClick = { showCreatePlaylist = true })
                    }

                    // All Tracks card
                    val allSelected = selectedPlaylistId == "all"
                    val allScale by animateFloatAsState(if (allSelected) 1.02f else 1f, spring(0.5f, 500f), label = "as")
                    Row(modifier = Modifier.fillMaxWidth().scale(allScale).padding(bottom = 8.dp)
                        .border(1.dp, if (allSelected) GreenPrimary else GreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
                            Text("No playlists yet.\nTap + New to create one.", color = GreenPrimary.copy(alpha = 0.4f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns.coerceIn(1, 5)),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(playlists, key = { _, pl -> pl.id }) { _, playlist ->
                                var isRenaming by remember { mutableStateOf(false) }
                                var renameText by remember(playlist.name) { mutableStateOf(playlist.name) }
                                val isHov = hoveredPlaylistId == playlist.id
                                val isSel = selectedPlaylistId == playlist.id
                                val cardScale by animateFloatAsState(when { isHov -> 1.06f; isSel -> 1.02f; else -> 1f }, spring(0.5f, 500f), label = "cs")

                                // Compact card — single border wrapping cover + info row
                                Column(modifier = Modifier.scale(cardScale)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isHov) GreenPrimary.copy(alpha = 0.15f) else if (isSel) GreenPrimary.copy(alpha = 0.1f) else BrownMid.copy(alpha = 0.6f))
                                    .border(1.dp, if (isHov || isSel) GreenPrimary else GreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        playlistBounds[playlist.id] = androidx.compose.ui.geometry.Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                                    }
                                    .clickable { if (!isRenaming) selectedPlaylistId = playlist.id }
                                ) {
                                    // Square cover (fills card width)
                                    PlaylistCoverView(
                                        playlist = playlist,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    )

                                    // Info row — name + count + buttons, all on one line
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 2.dp)) {
                                            if (isRenaming) {
                                                BasicTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true,
                                                    textStyle = TextStyle(color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                                    cursorBrush = SolidColor(GreenPrimary),
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = {
                                                        val u = playlists.map { if (it.id == playlist.id) it.copy(name = renameText) else it }
                                                        playlists = u; AppPreferences.savePlaylists(context, u); isRenaming = false
                                                    }), modifier = Modifier.fillMaxWidth())
                                            } else {
                                                Text(playlist.name, color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("${playlist.trackUris.size} tracks", color = GreenPrimary.copy(alpha = 0.6f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                            if (isRenaming) {
                                                IconButton(onClick = {
                                                    val u = playlists.map { if (it.id == playlist.id) it.copy(name = renameText) else it }
                                                    playlists = u; AppPreferences.savePlaylists(context, u); isRenaming = false
                                                }, modifier = Modifier.size(20.dp)) {
                                                    Icon(Icons.Default.Check, "Done", tint = GreenPrimary, modifier = Modifier.size(12.dp))
                                                }
                                            } else {
                                                IconButton(onClick = { isRenaming = true }, modifier = Modifier.size(20.dp)) {
                                                    Icon(Icons.Default.Edit, "Rename", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                                }
                                                IconButton(onClick = {
                                                    val u = playlists.filter { it.id != playlist.id }
                                                    playlists = u; AppPreferences.savePlaylists(context, u)
                                                    if (selectedPlaylistId == playlist.id) selectedPlaylistId = "all"
                                                }, modifier = Modifier.size(20.dp)) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = GreenPrimary.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                                }
                                            }
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

        // Drag-to-playlist ghost
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

        // Reorder ghost
        val reorderGhostTrack = reorderDraggingIndex?.let { displayedTracks.getOrNull(it) }
        if (reorderGhostTrack != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(99f)) {
                Box(modifier = Modifier.offset { IntOffset(reorderDragPosition.x.roundToInt() - 20, reorderDragPosition.y.roundToInt() - 20) }
                    .widthIn(max = 280.dp)
                    .background(BrownMid.copy(alpha = 0.95f), RoundedCornerShape(6.dp))
                    .border(1.dp, GreenPrimary.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DragHandle, null, tint = GreenPrimary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        Column {
                            Text(reorderGhostTrack.title, color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(reorderGhostTrack.artist, color = GreenPrimary.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onConfirm = { name, coverUri, offsetX, offsetY, scale ->
                val newPl = VirtualPlaylist(
                    id = UUID.randomUUID().toString(), name = name,
                    coverUri = coverUri, coverOffsetX = offsetX, coverOffsetY = offsetY, coverScale = scale
                )
                val updated = playlists + newPl
                playlists = updated; AppPreferences.savePlaylists(context, updated)
                showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false }
        )
    }
}

// ── Green Pill Scrollbar ──────────────────────────────────────────────────────
@Composable
fun GreenScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val totalItems = listState.layoutInfo.totalItemsCount
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
    val scrollRange = (totalItems - visibleCount).coerceAtLeast(0)
    if (scrollRange == 0 || totalItems == 0) return

    var trackHeightPx by remember { mutableStateOf(1f) }
    val thumbFraction = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.07f, 0.88f)
    val scrollFraction = listState.firstVisibleItemIndex.toFloat() / scrollRange.toFloat()

    val thumbHeightPx = trackHeightPx * thumbFraction
    val thumbOffsetPx = (trackHeightPx - thumbHeightPx) * scrollFraction.coerceIn(0f, 1f)

    Box(modifier = modifier
        .width(8.dp)
        .onGloballyPositioned { trackHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
    ) {
        Box(Modifier.fillMaxSize().background(GreenPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp)))
        val thumbH = with(density) { thumbHeightPx.toDp() }
        val thumbOff = with(density) { thumbOffsetPx.toDp() }
        Box(Modifier
            .width(8.dp)
            .height(thumbH)
            .offset(y = thumbOff)
            .background(GreenPrimary, RoundedCornerShape(4.dp))
            .pointerInput(scrollRange) {
                detectDragGestures { _, dragAmount ->
                    val tPx = trackHeightPx.coerceAtLeast(1f)
                    val thPx = thumbHeightPx.coerceAtLeast(1f)
                    val frac = dragAmount.y / (tPx - thPx).coerceAtLeast(1f)
                    val delta = (frac * scrollRange).roundToInt()
                    val target = (listState.firstVisibleItemIndex + delta).coerceIn(0, scrollRange)
                    coroutineScope.launch { listState.scrollToItem(target) }
                }
            }
        )
    }
}

// ── Playlist Cover View ───────────────────────────────────────────────────────
@Composable
fun PlaylistCoverView(playlist: VirtualPlaylist, modifier: Modifier) {
    if (playlist.coverUri.isEmpty()) {
        Box(modifier = modifier.background(BrownDark), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 22.sp) }
        return
    }
    val context = LocalContext.current
    val uriStr = playlist.coverUri
    val uri = remember(uriStr) { try { Uri.parse(uriStr) } catch (e: Exception) { null } }
    val mimeType = remember(uriStr) { try { uri?.let { context.contentResolver.getType(it) } ?: "" } catch (e: Exception) { "" } }

    when {
        mimeType.startsWith("video/") && uri != null -> {
            AndroidView(
                factory = { ctx -> VideoView(ctx).apply { setVideoURI(uri); setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() } } },
                onRelease = { it.stopPlayback() },
                modifier = modifier
            )
        }
        mimeType == "image/gif" && uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try {
                            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                            val drawable = ImageDecoder.decodeDrawable(src)
                            setImageDrawable(drawable)
                            (drawable as? AnimatedImageDrawable)?.start()
                        } catch (e: Exception) {}
                    }
                },
                modifier = modifier
            )
        }
        mimeType.startsWith("image/") && uri != null -> {
            val bitmap = remember(uriStr) {
                try { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (e: Exception) { null }
            }
            val bmp = bitmap
            if (bmp != null) {
                val oX = playlist.coverOffsetX; val oY = playlist.coverOffsetY; val sc = playlist.coverScale.coerceAtLeast(1f)
                Canvas(modifier = modifier) {
                    val bW = bmp.width.toFloat().coerceAtLeast(1f); val bH = bmp.height.toFloat().coerceAtLeast(1f)
                    val cW = size.width; val cH = size.height
                    val base = maxOf(cW / bW, cH / bH) * sc
                    val sW = (cW / base).coerceAtMost(bW); val sH = (cH / base).coerceAtMost(bH)
                    val cx = bW / 2f + oX / base; val cy = bH / 2f + oY / base
                    val sl = (cx - sW / 2f).coerceIn(0f, (bW - sW).coerceAtLeast(0f))
                    val st = (cy - sH / 2f).coerceIn(0f, (bH - sH).coerceAtLeast(0f))
                    drawImage(bmp, srcOffset = IntOffset(sl.toInt(), st.toInt()),
                        srcSize = IntSize(sW.toInt().coerceAtLeast(1), sH.toInt().coerceAtLeast(1)),
                        dstOffset = IntOffset.Zero, dstSize = IntSize(cW.toInt().coerceAtLeast(1), cH.toInt().coerceAtLeast(1)))
                }
            } else {
                Box(modifier = modifier.background(BrownDark), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 22.sp) }
            }
        }
        else -> {
            Box(modifier = modifier.background(BrownDark), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 22.sp) }
        }
    }
}

// ── Create Playlist Dialog ────────────────────────────────────────────────────
@Composable
fun CreatePlaylistDialog(
    onConfirm: (name: String, coverUri: String, offsetX: Float, offsetY: Float, scale: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("New Playlist") }
    var coverUri by remember { mutableStateOf("") }
    var coverMimeType by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var coverOffsetX by remember { mutableStateOf(0f) }
    var coverOffsetY by remember { mutableStateOf(0f) }
    var coverScale by remember { mutableStateOf(1f) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            coverUri = it.toString()
            coverMimeType = try { context.contentResolver.getType(it) ?: "" } catch (e: Exception) { "" }
            coverOffsetX = 0f; coverOffsetY = 0f; coverScale = 1f
            if (coverMimeType.startsWith("image/") && coverMimeType != "image/gif") {
                previewBitmap = try { context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } } catch (e: Exception) { null }
            } else { previewBitmap = null }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.width(360.dp).clip(RoundedCornerShape(12.dp)).drawBehind { drawCheckerboard() }.border(2.dp, GreenPrimary, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Create Playlist", color = GreenPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                HorizontalDivider(color = GreenPrimary.copy(alpha = 0.3f))

                // Name field
                Text("Name:", color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = TextStyle(color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(GreenPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GreenPrimary, RoundedCornerShape(6.dp)).padding(8.dp))

                // Cover section
                Text("Cover (optional):", color = GreenPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    // Preview square
                    Box(modifier = Modifier.size(130.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, GreenPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))) {
                        when {
                            coverUri.isEmpty() -> {
                                Box(Modifier.fillMaxSize().background(BrownDark), contentAlignment = Alignment.Center) {
                                    Text("🎵\nNo cover", fontSize = 14.sp, textAlign = TextAlign.Center, color = GreenPrimary.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                                }
                            }
                            coverMimeType.startsWith("video/") -> {
                                val uri = remember(coverUri) { try { Uri.parse(coverUri) } catch (e: Exception) { null } }
                                if (uri != null) {
                                    AndroidView(factory = { ctx -> VideoView(ctx).apply { setVideoURI(uri); setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() } } },
                                        onRelease = { it.stopPlayback() }, modifier = Modifier.fillMaxSize())
                                }
                            }
                            coverMimeType == "image/gif" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                                val uri = remember(coverUri) { try { Uri.parse(coverUri) } catch (e: Exception) { null } }
                                if (uri != null) {
                                    AndroidView(factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                            try {
                                                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                                                val drawable = ImageDecoder.decodeDrawable(src)
                                                setImageDrawable(drawable); (drawable as? AnimatedImageDrawable)?.start()
                                            } catch (e: Exception) {}
                                        }
                                    }, modifier = Modifier.fillMaxSize())
                                }
                            }
                            previewBitmap != null -> {
                                val bmp = previewBitmap!!
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val bW = bmp.width.toFloat().coerceAtLeast(1f); val bH = bmp.height.toFloat().coerceAtLeast(1f)
                                    val cW = size.width; val cH = size.height
                                    val base = maxOf(cW / bW, cH / bH) * coverScale.coerceAtLeast(1f)
                                    val sW = (cW / base).coerceAtMost(bW); val sH = (cH / base).coerceAtMost(bH)
                                    val cx = bW / 2f + coverOffsetX / base; val cy = bH / 2f + coverOffsetY / base
                                    val sl = (cx - sW / 2f).coerceIn(0f, (bW - sW).coerceAtLeast(0f))
                                    val st = (cy - sH / 2f).coerceIn(0f, (bH - sH).coerceAtLeast(0f))
                                    drawImage(bmp, srcOffset = IntOffset(sl.toInt(), st.toInt()),
                                        srcSize = IntSize(sW.toInt().coerceAtLeast(1), sH.toInt().coerceAtLeast(1)),
                                        dstOffset = IntOffset.Zero, dstSize = IntSize(cW.toInt().coerceAtLeast(1), cH.toInt().coerceAtLeast(1)))
                                }
                            }
                            else -> {
                                Box(Modifier.fillMaxSize().background(BrownDark), contentAlignment = Alignment.Center) {
                                    Text("⚠️", fontSize = 18.sp)
                                }
                            }
                        }
                    }

                    // Right side: pick button + crop sliders
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RaccoonButton(text = if (coverUri.isEmpty()) "Pick Cover" else "Change Cover",
                            small = true, onClick = { coverPickerLauncher.launch(arrayOf("image/*", "video/*")) })
                        if (coverUri.isNotEmpty() && previewBitmap != null) {
                            Text("Zoom:", color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Slider(value = coverScale, onValueChange = { coverScale = it }, valueRange = 1f..3f,
                                modifier = Modifier.fillMaxWidth().height(18.dp),
                                colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary, inactiveTrackColor = GreenPrimary.copy(alpha = 0.3f)))
                            Text("Pan X:", color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Slider(value = coverOffsetX, onValueChange = { coverOffsetX = it }, valueRange = -600f..600f,
                                modifier = Modifier.fillMaxWidth().height(18.dp),
                                colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary, inactiveTrackColor = GreenPrimary.copy(alpha = 0.3f)))
                            Text("Pan Y:", color = GreenPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Slider(value = coverOffsetY, onValueChange = { coverOffsetY = it }, valueRange = -600f..600f,
                                modifier = Modifier.fillMaxWidth().height(18.dp),
                                colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary, inactiveTrackColor = GreenPrimary.copy(alpha = 0.3f)))
                        }
                        if (coverUri.isNotEmpty() && (coverMimeType.startsWith("video/") || coverMimeType == "image/gif")) {
                            Text("Video/GIF covers\nplay in the card.", color = GreenPrimary.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                        }
                    }
                }

                HorizontalDivider(color = GreenPrimary.copy(alpha = 0.3f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RaccoonButton(text = "Create", highlighted = true, onClick = {
                        if (name.isNotBlank()) onConfirm(name.trim(), coverUri, coverOffsetX, coverOffsetY, coverScale)
                    })
                    RaccoonButton(text = "Cancel", onClick = onDismiss)
                }
            }
        }
    }
}
