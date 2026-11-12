package com.zzc.tripleplayer.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.zzc.tripleplayer.model.TileType
import com.zzc.tripleplayer.model.TileUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val BASE_MAX_EDGE = 720f
private const val IMAGE_MAX_EDGE = 1440

@Composable
fun VideoTile(
    tile: TileUiState,
    player: ExoPlayer,
    showMoveBar: Boolean,
    onPickVideo: () -> Unit,
    onPickImage: () -> Unit,
    onTapTile: () -> Unit,
    onMove: (Int) -> Unit,
    onSwitchVideo: (Int) -> Unit,
    onCalibrationChange: () -> Unit,
    onCloseTile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var boxW by remember { mutableFloatStateOf(0f) }
    var boxH by remember { mutableFloatStateOf(0f) }
    // 拖动进度条时的本地值，避免播放进度轮询和拖动互相打架
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    // 录制带旋转元数据的视频（下载/转发文件常见），解码上报的是未旋转尺寸，
                    // 90/270° 时交换宽高，否则横竖屏判断和紧凑布局全错
                    val rot = videoSize.unappliedRotationDegrees
                    if (rot == 90 || rot == 270) {
                        tile.videoW = videoSize.height
                        tile.videoH = videoSize.width
                    } else {
                        tile.videoW = videoSize.width
                        tile.videoH = videoSize.height
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // 初始尺寸从轨道 format 取：视频解码器是 lazy 初始化（surface 为 null 不建 codec），
                // onVideoSizeChanged 要等 TextureView 放好才触发，这里先回写打破死锁
                tracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected }?.let { g ->
                    val f = g.getTrackFormat(0)
                    // 轨道 format 不带旋转元数据（media3 1.4.1），先按原始宽高回写；
                    // 带旋转的视频等 onVideoSizeChanged 的 unappliedRotationDegrees 纠正
                    if (f.width > 0 && f.height > 0 && tile.videoW == 0) {
                        tile.videoW = f.width
                        tile.videoH = f.height
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            // 退出播放页时 player 可能已 release，removeListener 容错
            runCatching { player.removeListener(listener) }
        }
    }

    LaunchedEffect(tile.hasVideo, tile.type) {
        while (tile.hasVideo && tile.type == TileType.VIDEO) {
            runCatching {
                tile.positionMs = player.currentPosition
                tile.durationMs = player.duration.coerceAtLeast(0L)
                tile.playing = player.isPlaying
            }
            delay(300)
        }
    }

    // 图片格子：按 uri 解码（限制最大边长防爆内存），尺寸回写后走同一套 fitFactor 变换
    if (tile.type == TileType.IMAGE) {
        val context = LocalContext.current
        LaunchedEffect(tile.uri, tile.decodeSeq) {
            if (tile.uri.isEmpty()) return@LaunchedEffect
            val result = withContext(Dispatchers.IO) {
                try {
                    val cr = context.contentResolver
                    val uri = Uri.parse(tile.uri)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                    tile.videoW = bounds.outWidth
                    tile.videoH = bounds.outHeight
                    var sample = 1
                    var maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
                    while (maxEdge / 2 >= IMAGE_MAX_EDGE) {
                        sample *= 2
                        maxEdge /= 2
                    }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    cr.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
            if (result != null) {
                tile.imageBitmap = result
                tile.loadError = ""
            } else {
                tile.loadError = "图片加载失败"
            }
        }
    }

    Box(
        modifier
            // 底色由调用方传入（测试用分色），这里不再固定黑色
            .clipToBounds()
            .onSizeChanged { size ->
                boxW = size.width.toFloat()
                boxH = size.height.toFloat()
            }
            // key 绑 tile：换位后本格子的 tile 对象换了身份，手势闭包要跟着换，
            // 否则捏合/拖动会改到旧格子的校准状态
            .pointerInput(tile) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!tile.hasVideo) return@detectTransformGestures
                    tile.scale = (tile.scale * zoom).coerceIn(0.1f, 10f)
                    tile.tx += pan.x
                    tile.ty += pan.y
                    onCalibrationChange()
                }
            }
            .pointerInput(tile) {
                detectTapGestures(
                    onTap = { if (tile.hasVideo) onTapTile() }
                )
            }
    ) {
        // TextureView 布局尺寸固定为视频比例的基准大小（最长边 720），此后不再变化；
        // 等比适配容器、校准放大全部走 graphicsLayer 变换，布局尺寸恒定避免表面与内容错位。
        if (tile.videoW > 0 && tile.videoH > 0 && tile.hasVideo) {
            val baseW: Float
            val baseH: Float
            if (tile.videoW >= tile.videoH) {
                baseW = BASE_MAX_EDGE
                baseH = BASE_MAX_EDGE * tile.videoH / tile.videoW
            } else {
                baseH = BASE_MAX_EDGE
                baseW = BASE_MAX_EDGE * tile.videoW / tile.videoH
            }
            // 90/270° 旋转后画面视觉宽高互换，fitFactor 按交换后的比例算，保证旋转后仍撑满可显示区域。
            // 画面保持源比例完整显示（留黑边），想去掉缝隙用双指缩放手动调
            val fitFactor = if (boxW > 0 && boxH > 0) {
                if (tile.rotation % 180f == 0f) {
                    minOf(boxW / baseW, boxH / baseH)
                } else {
                    minOf(boxW / baseH, boxH / baseW)
                }
            } else 1f
            val transform = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = fitFactor * tile.scale
                    scaleY = fitFactor * tile.scale
                    translationX = tile.tx
                    translationY = tile.ty
                    rotationZ = tile.rotation
                }

            if (tile.type == TileType.IMAGE) {
                val bmp = tile.imageBitmap
                if (bmp != null) {
                    val density = LocalDensity.current
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = transform.requiredSize(
                            with(density) { baseW.toDp() },
                            with(density) { baseH.toDp() }
                        )
                    )
                }
            } else {
                // 换位时格子与播放器实例一起交换（播放器不重新 prepare，视频不中断），
                // player 变化后要把播放器重新绑到本格子的 surface
                var surfaceView by remember { mutableStateOf<android.view.TextureView?>(null) }
                LaunchedEffect(player, surfaceView) {
                    surfaceView?.let { runCatching { player.setVideoTextureView(it) } }
                }
                AndroidView(
                    factory = { ctx ->
                        android.view.TextureView(ctx).also { surfaceView = it }
                    },
                    update = { view ->
                        val params = view.layoutParams
                        params.width = baseW.toInt()
                        params.height = baseH.toInt()
                        view.layoutParams = params
                    },
                    // 必须用 requiredSize 钉死布局尺寸：基准宽（720px）可能大于格子宽，
                    // 若被父约束钳制，surface 会把画面压变形再被 fitFactor 缩小（横屏视频又小又扁）
                    modifier = transform.requiredSize(
                        with(LocalDensity.current) { baseW.toDp() },
                        with(LocalDensity.current) { baseH.toDp() }
                    )
                )
            }
        }

        if (!tile.hasVideo) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onPickVideo,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp
                    )
                ) {
                    Text("＋ 添加视频", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onPickImage,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp
                    )
                ) {
                    Text("＋ 添加图片", fontSize = 14.sp)
                }
            }
        }

        if (tile.loadError.isNotEmpty()) {
            Text(
                "加载失败: " + tile.loadError,
                fontSize = 11.sp,
                color = Color(0xFFFF6666),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000))
                    .padding(8.dp)
            )
        }

        if (tile.hasVideo && tile.showControls) {
            if (showMoveBar) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(6.dp)
                        .background(Color(0x66000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        // 消费点击：点在顶部操作区不触发格子的显隐切换
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("换位", fontSize = 13.sp, color = Color.White)
                    Spacer(Modifier.padding(4.dp))
                    SmallButton("⇦") { onMove(-1) }
                    Spacer(Modifier.padding(4.dp))
                    SmallButton("⇨") { onMove(1) }
                    Spacer(Modifier.padding(4.dp))
                    SmallButton("✕", Color(0xFFFF8888)) { onCloseTile() }
                }
            }

            if (tile.type == TileType.VIDEO) {
                val density = LocalDensity.current
                // 紧凑布局下竖屏格子很窄（约 160dp），完整控制条（音量条+多行按钮）会挤爆，换精简版
                val narrow = boxW > 0f && boxW / density.density < 200f
                if (narrow) {
                    CompactControls(
                        tile = tile,
                        player = player,
                        dragging = dragging,
                        dragPosition = dragPosition,
                        showClose = !showMoveBar,
                        onDragStart = { dragging = true; dragPosition = tile.positionMs.toFloat() },
                        onDrag = { dragPosition = it },
                        onDragEnd = {
                            dragging = false
                            player.seekTo(dragPosition.toLong())
                        },
                        onVolume = { v ->
                            tile.volume = v
                            player.volume = v
                            onCalibrationChange()
                        },
                        onSwitchVideo = onSwitchVideo,
                        onRotate = {
                            tile.rotation = (tile.rotation + 90f) % 360f
                            onCalibrationChange()
                        },
                        onClose = onCloseTile,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    )
                } else {
                    Controls(
                        tile = tile,
                        player = player,
                        dragging = dragging,
                        dragPosition = dragPosition,
                        showClose = !showMoveBar,
                        onDragStart = { dragging = true; dragPosition = tile.positionMs.toFloat() },
                        onDrag = { dragPosition = it },
                        onDragEnd = {
                            dragging = false
                            player.seekTo(dragPosition.toLong())
                        },
                        onVolume = { v ->
                            tile.volume = v
                            player.volume = v
                            onCalibrationChange()
                        },
                        onRotate = {
                            tile.rotation = (tile.rotation + 90f) % 360f
                            onCalibrationChange()
                        },
                        onSwitchVideo = onSwitchVideo,
                        onClose = onCloseTile,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            // 消费点击：点在控制条上不触发格子的显隐切换
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    )
                }
            } else {
                // 图片格子：无播放/进度/音量/切视频，只保留旋转和关闭
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(Color(0x66000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallButton("⟳") {
                        tile.rotation = (tile.rotation + 90f) % 360f
                        onCalibrationChange()
                    }
                    if (!showMoveBar) {
                        Spacer(Modifier.padding(6.dp))
                        SmallButton("✕", Color(0xFFFF8888)) { onCloseTile() }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun Controls(
    tile: TileUiState,
    player: ExoPlayer,
    dragging: Boolean,
    dragPosition: Float,
    showClose: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onVolume: (Float) -> Unit,
    onRotate: () -> Unit,
    onSwitchVideo: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVolumePanel by remember { mutableStateOf(false) }
    Column(
        modifier
            .padding(8.dp)
            .background(Color(0x66000000), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        if (showVolumePanel) {
            // 音量弹层：误触只会打开/关闭面板，拖动这里的大滑条才真正改音量
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Text("🔊", fontSize = 14.sp)
                Slider(
                    value = tile.volume,
                    onValueChange = onVolume,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(40.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF1A73E8)
                    )
                )
                Text(
                    "${(tile.volume * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(tile.positionMs) + " / " + formatTime(tile.durationMs),
                fontSize = 12.sp,
                color = Color.White
            )
        }
        Slider(
            value = if (dragging) dragPosition else tile.positionMs.toFloat(),
            onValueChange = {
                if (!dragging) onDragStart()
                onDrag(it)
            },
            onValueChangeFinished = onDragEnd,
            valueRange = 0f..(if (tile.durationMs > 0) tile.durationMs.toFloat() else 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF1A73E8)
            )
        )
        // ⏮ ▶ ⏭ 居中，🔊 ⟳（单格模式含 ✕）右对齐；按钮间留边距防误触
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            SmallButton("⏮") { onSwitchVideo(-1) }
            Spacer(Modifier.padding(8.dp))
            SmallButton(if (tile.playing) "⏸" else "▶") {
                if (player.isPlaying) player.pause() else player.play()
            }
            Spacer(Modifier.padding(8.dp))
            SmallButton("⏭") { onSwitchVideo(1) }
            Spacer(Modifier.weight(1f))
            SmallButton(if (tile.volume > 0.01f) "🔊" else "🔇") { showVolumePanel = !showVolumePanel }
            Spacer(Modifier.padding(8.dp))
            SmallButton("⟳") { onRotate() }
            if (showClose) {
                Spacer(Modifier.padding(8.dp))
                SmallButton("✕", Color(0xFFFF8888)) { onClose() }
            }
        }
    }
}

// Material3 Button 有最小尺寸（58x40dp）压不小，操作栏用轻量文本按钮；dense 用于窄格子
@Composable
private fun SmallButton(
    text: String,
    tint: Color = Color.White,
    dense: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text,
        fontSize = if (dense) 13.sp else 15.sp,
        color = tint,
        modifier = Modifier
            .background(Color(0x40000000), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(
                horizontal = if (dense) 6.dp else 10.dp,
                vertical = if (dense) 3.dp else 5.dp
            )
    )
}

// 窄格子（紧凑布局下的竖屏视频）用的精简控制条：进度条 + 单行按钮，无音量条
@Composable
private fun CompactControls(
    tile: TileUiState,
    player: ExoPlayer,
    dragging: Boolean,
    dragPosition: Float,
    showClose: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onVolume: (Float) -> Unit,
    onSwitchVideo: (Int) -> Unit,
    onRotate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVolumePanel by remember { mutableStateOf(false) }
    Column(
        modifier
            .padding(4.dp)
            .background(Color(0x66000000), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        if (showVolumePanel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Slider(
                    value = tile.volume,
                    onValueChange = onVolume,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF1A73E8)
                    )
                )
                Text(
                    "${(tile.volume * 100).roundToInt()}%",
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = if (dragging) dragPosition else tile.positionMs.toFloat(),
                onValueChange = {
                    if (!dragging) onDragStart()
                    onDrag(it)
                },
                onValueChangeFinished = onDragEnd,
                valueRange = 0f..(if (tile.durationMs > 0) tile.durationMs.toFloat() else 1f),
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF1A73E8)
                )
            )
            Text(
                formatTime(tile.positionMs) + "/" + formatTime(tile.durationMs),
                fontSize = 10.sp,
                color = Color.White
            )
        }
        // 5~6 个按钮 + 最小边距，保证 190px 宽的窄格子也放得下
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton("⏮", dense = true) { onSwitchVideo(-1) }
            Spacer(Modifier.padding(3.dp))
            SmallButton(if (tile.playing) "⏸" else "▶", dense = true) {
                if (player.isPlaying) player.pause() else player.play()
            }
            Spacer(Modifier.padding(3.dp))
            SmallButton("⏭", dense = true) { onSwitchVideo(1) }
            Spacer(Modifier.weight(1f))
            SmallButton(if (tile.volume > 0.01f) "🔊" else "🔇", dense = true) {
                showVolumePanel = !showVolumePanel
            }
            Spacer(Modifier.padding(3.dp))
            SmallButton("⟳", dense = true) { onRotate() }
            if (showClose) {
                Spacer(Modifier.padding(3.dp))
                SmallButton("✕", Color(0xFFFF8888), dense = true) { onClose() }
            }
        }
    }
}
