package com.zzc.tripleplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.zzc.tripleplayer.data.CalibrationStore
import com.zzc.tripleplayer.model.TileType
import com.zzc.tripleplayer.model.TileUiState
import com.zzc.tripleplayer.model.VideoItem
import com.zzc.tripleplayer.ui.FolderListScreen
import com.zzc.tripleplayer.ui.FolderVideoListScreen
import com.zzc.tripleplayer.ui.MediaPickerScreen
import com.zzc.tripleplayer.ui.PickItem
import com.zzc.tripleplayer.ui.VideoTile
import com.zzc.tripleplayer.ui.formatDurationPublic

class MainActivity : ComponentActivity() {
    private val tiles = mutableListOf<TileUiState>()
    private val players = mutableListOf<ExoPlayer>()
    private val videoList = mutableStateOf<List<VideoItem>>(emptyList())
    private val imageList = mutableStateOf<List<PickItem>>(emptyList())

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            loadVideoList()
            loadImageList()
        }
    }

    private val layoutPrefs by lazy {
        getSharedPreferences("layout_settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CalibrationStore.init(this)
        requestMediaPermissions()
        hideSystemBars()
        setContent { TriplePlayerApp() }
    }

    override fun onDestroy() {
        super.onDestroy()
        players.forEach { it.release() }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    private fun requestMediaPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            loadVideoList()
            loadImageList()
        } else {
            permLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadVideoList() {
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA
        )
        val result = mutableListOf<VideoItem>()
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            proj, null, null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result.add(
                    VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        name = c.getString(nameCol) ?: "",
                        durationMs = c.getLong(durCol),
                        folder = (c.getString(pathCol) ?: "").substringBeforeLast('/')
                    )
                )
            }
        }
        videoList.value = result
    }

    private fun loadImageList() {
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA
        )
        val result = mutableListOf<PickItem>()
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            proj, null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(dataCol) ?: ""
                result.add(
                    PickItem(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        label = "",
                        folder = path.substringBeforeLast('/')
                    )
                )
            }
        }
        imageList.value = result
    }

    // SAF document uri（.../video:123）与 MediaStore uri（.../media/123）尾部数字即 mediaId
    private fun mediaIdOf(uriStr: String): Long? =
        Regex("(\\d+)$").find(uriStr)?.groupValues?.get(1)?.toLongOrNull()

    @Composable
    private fun TriplePlayerApp() {
        var inPlayer by remember { mutableStateOf(false) }
        var tileCount by remember { mutableStateOf(0) }
        var multiMode by remember { mutableStateOf(false) }
        var showCountDialog by remember { mutableStateOf(false) }
        var pendingVideo by remember { mutableStateOf<VideoItem?>(null) }
        // null = 文件夹列表页；非 null = 正在浏览该文件夹的视频列表
        var viewingFolder by remember { mutableStateOf<String?>(null) }
        // >= 0 = 正在给该格子选媒体（内部选片弹层）
        var pickingTile by remember { mutableStateOf(-1) }
        var pickingImage by remember { mutableStateOf(false) }
        // 多视频布局：false = 经典（等分铺满），true = 紧凑（按画面比例贴齐居中）
        var compactLayout by remember {
            mutableStateOf(layoutPrefs.getBoolean("compact_layout", false))
        }
        var showLayoutDialog by remember { mutableStateOf(false) }

        fun loadTile(i: Int, uri: Uri, type: TileType = TileType.VIDEO) {
            val t = tiles[i]
            val p = players[i]
            t.reset()
            t.uri = uri.toString()
            t.type = type
            t.hasVideo = true
            t.showControls = true
            // 重选同一文件时复原校准（含音量）
            CalibrationStore.load(uri)?.let { s ->
                t.rotation = s.rotation
                t.scale = s.scale
                t.tx = s.tx
                t.ty = s.ty
                t.volume = s.volume
            }
            if (type == TileType.VIDEO) {
                p.setMediaItem(MediaItem.fromUri(uri))
                p.volume = t.volume
                p.prepare()
                p.playWhenReady = false
            } else {
                t.decodeSeq++
            }
        }

        fun reload(i: Int) {
            val t = tiles[i]
            val p = players[i]
            if (!t.hasVideo) {
                p.stop()
                p.clearMediaItems()
                return
            }
            if (t.type == TileType.IMAGE) {
                // 图片位图随 TileSnap 迁移，无需重建
                return
            }
            p.setMediaItem(MediaItem.fromUri(Uri.parse(t.uri)))
            p.volume = t.volume
            p.prepare()
            p.playWhenReady = false
        }

        fun saveCalibration(i: Int) {
            val t = tiles[i]
            if (t.uri.isNotEmpty()) {
                CalibrationStore.save(Uri.parse(t.uri), t.rotation, t.scale, t.tx, t.ty, t.volume)
            }
        }

        fun closeTile(i: Int) {
            players[i].stop()
            players[i].clearMediaItems()
            tiles[i].reset()
        }

        fun swapTiles(a: Int, b: Int) {
            val ta = tiles[a]
            val tb = tiles[b]
            if (!ta.hasVideo && !tb.hasVideo) return
            val sa = TileSnap(ta)
            val sb = TileSnap(tb)
            ta.reset()
            tb.reset()
            sb.restoreTo(ta)
            sa.restoreTo(tb)
            reload(a)
            reload(b)
        }

        fun onMove(i: Int, dir: Int) {
            val j = i + dir
            if (j in 0 until tileCount) swapTiles(i, j)
        }

        fun onSwitchVideo(i: Int, dir: Int) {
            val items = videoList.value
            if (items.isEmpty()) return
            val cur = mediaIdOf(tiles[i].uri)
            val idx = items.indexOfFirst { mediaIdOf(it.uri.toString()) == cur }
            if (idx < 0) return
            // 只在当前视频所在文件夹内切换，到头即停
            val folderVideos = items.filter { it.folder == items[idx].folder }
            val fIdx = folderVideos.indexOfFirst { it.id == items[idx].id }
            val next = fIdx + dir
            if (next in folderVideos.indices) loadTile(i, folderVideos[next].uri)
            else Toast.makeText(
                this@MainActivity,
                if (dir > 0) "已经是最后一个视频" else "已经是第一个视频",
                Toast.LENGTH_SHORT
            ).show()
        }

        fun onTileTapped(i: Int) {
            tiles[i].showControls = !tiles[i].showControls
        }

        fun enterPlayer(count: Int, first: VideoItem?) {
            players.forEach { it.release() }
            players.clear()
            tiles.clear()
            repeat(count) { idx ->
                tiles.add(TileUiState(idx))
                players.add(
                    ExoPlayer.Builder(this@MainActivity).build().apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        playWhenReady = false
                    }
                )
            }
            tileCount = count
            inPlayer = true
            if (first != null) loadTile(0, first.uri)
        }

        fun exitPlayer() {
            players.forEach { it.release() }
            players.clear()
            tiles.clear()
            tileCount = 0
            pickingTile = -1
            inPlayer = false
        }

        BackHandler(enabled = inPlayer) { exitPlayer() }
        BackHandler(enabled = !inPlayer && viewingFolder != null) { viewingFolder = null }

        fun pickAndLoad(i: Int, isImage: Boolean) {
            pickingTile = i
            pickingImage = isImage
        }

        if (inPlayer) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                val screenW = maxWidth
                val screenH = maxHeight
                // 紧凑布局：每格宽度 = 该格画面按屏高适配后的视觉宽度（90/270° 旋转后宽高互换），
                // 格子贴齐、整体居中；总宽超屏时整体等比压缩。空格子按 16:9 估宽。
                val compact = compactLayout && tileCount > 1
                val widths = if (compact) {
                    tiles.map { t ->
                        val vw = t.videoW
                        val vh = t.videoH
                        val aspect = if (vw > 0 && vh > 0) {
                            if (t.rotation % 180f != 0f) vh.toFloat() / vw else vw.toFloat() / vh
                        } else 16f / 9f
                        screenH * aspect
                    }
                } else {
                    List(tileCount) { screenW / tileCount }
                }
                val totalW = widths.fold(0.dp) { acc, w -> acc + w }
                var x = if (compact) (screenW - totalW) / 2 else 0.dp
                tiles.forEachIndexed { i, t ->
                    VideoTile(
                        tile = t,
                        player = players[i],
                        showMoveBar = tileCount > 1,
                        onPickVideo = { pickAndLoad(i, false) },
                        onPickImage = { pickAndLoad(i, true) },
                        onTapTile = { onTileTapped(i) },
                        onMove = { dir -> onMove(i, dir) },
                        onSwitchVideo = { dir -> onSwitchVideo(i, dir) },
                        onCalibrationChange = { saveCalibration(i) },
                        onCloseTile = { closeTile(i) },
                        modifier = Modifier
                            .size(widths[i], screenH)
                            .offset(x = x)
                            .zIndex(1f)
                    )
                    x += widths[i]
                }

                if (pickingTile >= 0) {
                    if (pickingImage) {
                        MediaPickerScreen(
                            items = imageList.value,
                            title = "选择图片",
                            isImage = true,
                            onPick = { uri ->
                                loadTile(pickingTile, uri, TileType.IMAGE)
                                pickingTile = -1
                            },
                            onDismiss = { pickingTile = -1 },
                            modifier = Modifier.zIndex(2f)
                        )
                    } else {
                        MediaPickerScreen(
                            items = videoList.value.map {
                                PickItem(it.id, it.uri, formatDurationPublic(it.durationMs), it.folder)
                            },
                            title = "选择视频",
                            isImage = false,
                            onPick = { uri ->
                                loadTile(pickingTile, uri)
                                pickingTile = -1
                            },
                            onDismiss = { pickingTile = -1 },
                            modifier = Modifier.zIndex(2f)
                        )
                    }
                }
            }
        } else {
            val folder = viewingFolder
            if (folder == null) {
                FolderListScreen(
                    videos = videoList.value,
                    multiMode = multiMode,
                    onToggleMulti = { multiMode = it },
                    onOpenSettings = { showLayoutDialog = true },
                    onOpenFolder = { viewingFolder = it }
                )
            } else {
                val folderVideos = videoList.value.filter { it.folder == folder }
                FolderVideoListScreen(
                    folderName = folder.substringAfterLast('/'),
                    videos = folderVideos,
                    multiMode = multiMode,
                    onToggleMulti = { multiMode = it },
                    onOpenSettings = { showLayoutDialog = true },
                    onSelect = { v ->
                        if (multiMode) {
                            pendingVideo = v
                            showCountDialog = true
                        } else {
                            enterPlayer(1, v)
                        }
                    },
                    onBack = { viewingFolder = null }
                )
            }
        }

        if (showLayoutDialog) {
            AlertDialog(
                onDismissRequest = { showLayoutDialog = false },
                title = { Text("多视频布局") },
                text = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    compactLayout = false
                                    layoutPrefs.edit().putBoolean("compact_layout", false).apply()
                                    showLayoutDialog = false
                                }
                        ) {
                            RadioButton(selected = !compactLayout, onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("经典")
                                Text(
                                    "等分铺满屏幕（现状）",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    compactLayout = true
                                    layoutPrefs.edit().putBoolean("compact_layout", true).apply()
                                    showLayoutDialog = false
                                }
                        ) {
                            RadioButton(selected = compactLayout, onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("紧凑")
                                Text(
                                    "格子按画面比例无缝挨着，整体居中",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showCountDialog) {
            AlertDialog(
                onDismissRequest = { showCountDialog = false },
                title = { Text("选择并排播放器数量") },
                text = { Text("点选的视频放入第一个格子，其余格子稍后手动添加") },
                confirmButton = {
                    Row {
                        listOf(2, 3, 4).forEach { n ->
                            TextButton(onClick = {
                                showCountDialog = false
                                enterPlayer(n, pendingVideo)
                            }) {
                                Text("${n} 个")
                            }
                        }
                    }
                }
            )
        }
    }
}

// 换位时的格子状态快照（视频进度与播放状态不迁移，重新加载后从头暂停）
private class TileSnap(
    val uri: String,
    val type: TileType,
    val volume: Float,
    val rotation: Float,
    val scale: Float,
    val tx: Float,
    val ty: Float,
    val videoW: Int,
    val videoH: Int,
    val durationMs: Long,
    val imageBitmap: ImageBitmap?
) {
    constructor(t: TileUiState) : this(
        t.uri, t.type, t.volume, t.rotation, t.scale, t.tx, t.ty,
        t.videoW, t.videoH, t.durationMs, t.imageBitmap
    )

    fun restoreTo(t: TileUiState) {
        t.uri = uri
        t.type = type
        t.hasVideo = uri.isNotEmpty()
        t.volume = volume
        t.rotation = rotation
        t.scale = scale
        t.tx = tx
        t.ty = ty
        t.videoW = videoW
        t.videoH = videoH
        t.durationMs = durationMs
        t.imageBitmap = imageBitmap
        t.decodeSeq++
        t.positionMs = 0
        t.playing = false
        t.showControls = false
        t.loadError = ""
    }
}
