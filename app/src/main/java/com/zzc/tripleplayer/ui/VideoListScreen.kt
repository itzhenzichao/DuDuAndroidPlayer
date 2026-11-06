package com.zzc.tripleplayer.ui

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Video.Thumbnails
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzc.tripleplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HeaderBg = Color(0xFF111111)

@Composable
private fun TopBar(
    title: String,
    showBack: Boolean,
    multiMode: Boolean,
    onToggleMulti: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Text(
                "←",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { onBack() }
            )
        }
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        // ⚙ 放在开关左边：Switch 触控目标会向右外扩，贴右边会吞掉点击
        Text(
            "⚙",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier
                .clickable { onOpenSettings() }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Text("多视频", color = Color.White, fontSize = 14.sp)
        Switch(checked = multiMode, onCheckedChange = onToggleMulti)
    }
}

@Composable
fun FolderListScreen(
    videos: List<VideoItem>,
    multiMode: Boolean,
    onToggleMulti: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolder: (String) -> Unit
) {
    val folders = remember(videos) {
        videos.groupBy { it.folder }.toList()
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopBar("视频文件夹", showBack = false, multiMode, onToggleMulti, onOpenSettings, onBack = {})
        if (folders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有视频文件", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // key 用完整路径：不同层级文件夹的尾段可能同名
                items(folders, key = { it.first }) { (path, items) ->
                    val name = path.substringAfterLast('/')
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161616))
                            .clickable { onOpenFolder(items.first().folder) }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF222222))
                        ) {
                            VideoThumb(items.first().id, Modifier.fillMaxSize())
                            Text(
                                "${items.size} 个视频",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0x88000000))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            name,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderVideoListScreen(
    folderName: String,
    videos: List<VideoItem>,
    multiMode: Boolean,
    onToggleMulti: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSelect: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopBar(folderName, showBack = true, multiMode, onToggleMulti, onOpenSettings, onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(videos, key = { it.id }) { v ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(v) }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color(0xFF222222))
                    ) {
                        VideoThumb(v.id, Modifier.fillMaxSize())
                        Text(
                            formatDuration(v.durationMs),
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color(0x88000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        v.name,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoThumb(videoId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(videoId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoId) {
        bmp = withContext(Dispatchers.IO) {
            try {
                Thumbnails.getThumbnail(
                    context.contentResolver, videoId, Thumbnails.MINI_KIND, null
                )
            } catch (_: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier)
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatDurationPublic(ms: Long): String = formatDuration(ms)

// 选片弹层的数据项：视频带时长 label，图片 label 为空
data class PickItem(
    val id: Long,
    val uri: Uri,
    val label: String,
    val folder: String
)

@Composable
private fun MediaThumb(id: Long, isImage: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(id, isImage) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id, isImage) {
        bmp = withContext(Dispatchers.IO) {
            try {
                if (isImage) {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                    )
                } else {
                    Thumbnails.getThumbnail(
                        context.contentResolver, id, Thumbnails.MINI_KIND, null
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier)
    }
}

// 播放页内部选片弹层：两级结构（文件夹 → 媒体网格），点媒体回填到指定格子
@Composable
fun MediaPickerScreen(
    items: List<PickItem>,
    title: String,
    isImage: Boolean,
    onPick: (Uri) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var viewingFolder by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = true) {
        if (viewingFolder != null) viewingFolder = null else onDismiss()
    }
    Column(modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewingFolder != null) {
                Text(
                    "←",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { viewingFolder = null }
                )
            }
            Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(
                "✕",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
        val f = viewingFolder
        if (f == null) {
            val folders = remember(items) {
                items.groupBy { it.folder }.toList()
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // key 用完整路径：不同层级文件夹的尾段可能同名
                items(folders, key = { it.first }) { (path, its) ->
                    val name = path.substringAfterLast('/')
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161616))
                            .clickable { viewingFolder = its.first().folder }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF222222))
                        ) {
                            MediaThumb(its.first().id, isImage, Modifier.fillMaxSize())
                            Text(
                                "${its.size} 个",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0x88000000))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            name,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            val folderItems = items.filter { it.folder == f }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folderItems, key = { it.id }) { v ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(v.uri) }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF222222))
                        ) {
                            MediaThumb(v.id, isImage, Modifier.fillMaxSize())
                            if (v.label.isNotEmpty()) {
                                Text(
                                    v.label,
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0x88000000))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
