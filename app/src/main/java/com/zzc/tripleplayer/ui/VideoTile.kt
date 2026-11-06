package com.zzc.tripleplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.zzc.tripleplayer.model.TileUiState
import kotlinx.coroutines.delay

private const val BASE_MAX_EDGE = 720f

@Composable
fun VideoTile(
    tile: TileUiState,
    player: ExoPlayer,
    onPickVideo: () -> Unit,
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
                    tile.videoW = videoSize.width
                    tile.videoH = videoSize.height
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // 初始尺寸从轨道 format 取：视频解码器是 lazy 初始化（surface 为 null 不建 codec），
                // onVideoSizeChanged 要等 TextureView 放好才触发，这里先回写打破死锁
                tracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected }?.let { g ->
                    val f = g.getTrackFormat(0)
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

    LaunchedEffect(tile.hasVideo) {
        while (tile.hasVideo) {
            runCatching {
                tile.positionMs = player.currentPosition
                tile.durationMs = player.duration.coerceAtLeast(0L)
                tile.playing = player.isPlaying
            }
            delay(300)
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { size ->
                boxW = size.width.toFloat()
                boxH = size.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!tile.hasVideo) return@detectTransformGestures
                    tile.scale = (tile.scale * zoom).coerceIn(0.1f, 10f)
                    tile.tx += pan.x
                    tile.ty += pan.y
                    onCalibrationChange()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (tile.hasVideo) onTapTile() }
                )
            }
            .border(
                BorderStroke(2.dp, Color(0xFF333333))
            )
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
            val fitFactor = if (boxW > 0 && boxH > 0) {
                minOf(boxW / baseW, boxH / baseH)
            } else 1f

            AndroidView(
                factory = { ctx ->
                    android.view.TextureView(ctx).also { player.setVideoTextureView(it) }
                },
                update = { view ->
                    val params = view.layoutParams
                    params.width = baseW.toInt()
                    params.height = baseH.toInt()
                    view.layoutParams = params
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = fitFactor * tile.scale
                        scaleY = fitFactor * tile.scale
                        translationX = tile.tx
                        translationY = tile.ty
                        rotationZ = tile.rotation
                    }
            )
        }

        if (!tile.hasVideo) {
            Button(
                onClick = onPickVideo,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("＋ 添加视频", fontSize = 14.sp)
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
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(4.dp)
                    .background(Color(0x66000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    // 消费点击：点在顶部操作区不触发格子的显隐切换
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("换位", fontSize = 11.sp, color = Color.White)
                Spacer(Modifier.padding(2.dp))
                SmallButton("⇦") { onMove(-1) }
                Spacer(Modifier.padding(2.dp))
                SmallButton("⇨") { onMove(1) }
            }

            Controls(
                tile = tile,
                player = player,
                dragging = dragging,
                dragPosition = dragPosition,
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
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onVolume: (Float) -> Unit,
    onRotate: () -> Unit,
    onSwitchVideo: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .padding(4.dp)
            .background(Color(0x66000000), RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton(if (tile.playing) "⏸" else "▶") {
                if (player.isPlaying) player.pause() else player.play()
            }
            Spacer(Modifier.padding(3.dp))
            Text(
                formatTime(tile.positionMs) + " / " + formatTime(tile.durationMs),
                fontSize = 10.sp,
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
                .height(18.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF1A73E8)
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔊", fontSize = 10.sp)
            Slider(
                value = tile.volume,
                onValueChange = onVolume,
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF1A73E8)
                )
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton("⏮") { onSwitchVideo(-1) }
            Spacer(Modifier.padding(2.dp))
            SmallButton("⏭") { onSwitchVideo(1) }
            Spacer(Modifier.padding(2.dp))
            SmallButton("⟳") { onRotate() }
            Spacer(Modifier.padding(2.dp))
            SmallButton("✕", Color(0xFFFF8888)) { onClose() }
        }
    }
}

// Material3 Button 有最小尺寸（58x40dp）压不小，操作栏用轻量文本按钮
@Composable
private fun SmallButton(text: String, tint: Color = Color.White, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = tint,
        modifier = Modifier
            .background(Color(0x40000000), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
