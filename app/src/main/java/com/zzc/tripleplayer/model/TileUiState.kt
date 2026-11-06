package com.zzc.tripleplayer.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TileUiState(val index: Int) {
    var uri by mutableStateOf("")
    var hasVideo by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var volume by mutableFloatStateOf(1f)
    var rotation by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    // 视频原始分辨率（onVideoSizeChanged 回写），用于等比缩放
    var videoW by mutableStateOf(0)
    var videoH by mutableStateOf(0)
    var showControls by mutableStateOf(false)
    var loadError by mutableStateOf("")

    fun reset() {
        uri = ""
        hasVideo = false
        playing = false
        positionMs = 0
        durationMs = 0
        volume = 1f
        rotation = 0f
        scale = 1f
        tx = 0f
        ty = 0f
        videoW = 0
        videoH = 0
        showControls = false
        loadError = ""
    }
}
