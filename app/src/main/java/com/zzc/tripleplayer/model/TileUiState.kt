package com.zzc.tripleplayer.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

enum class TileType { VIDEO, IMAGE }

class TileUiState(val index: Int) {
    var uri by mutableStateOf("")
    var type by mutableStateOf(TileType.VIDEO)
    var hasVideo by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    // 默认 5%：多路视频同时出声，满音量容易吓一跳；有校准记录时按记录恢复
    var volume by mutableFloatStateOf(0.05f)
    var rotation by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    // 媒体原始分辨率（视频：onVideoSizeChanged 回写；图片：decodeBounds 回写），用于等比缩放
    var videoW by mutableStateOf(0)
    var videoH by mutableStateOf(0)
    // 图片格子的解码结果（按 uri 解码，换位/重载时重建）
    var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    // 解码触发序号：loadTile/换位后自增，驱动 VideoTile 重新解码图片
    var decodeSeq by mutableLongStateOf(0L)
    var showControls by mutableStateOf(false)
    var loadError by mutableStateOf("")

    fun reset() {
        uri = ""
        type = TileType.VIDEO
        hasVideo = false
        playing = false
        positionMs = 0
        durationMs = 0
        volume = 0.05f
        rotation = 0f
        scale = 1f
        tx = 0f
        ty = 0f
        videoW = 0
        videoH = 0
        imageBitmap = null
        decodeSeq = 0
        showControls = false
        loadError = ""
    }
}
