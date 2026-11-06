package com.zzc.tripleplayer.data

import android.content.Context
import android.net.Uri

/**
 * 校准信息按视频 uri 持久化：重选同一文件时复原旋转/缩放/平移/音量。
 */
object CalibrationStore {
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)
    }

    fun load(uri: Uri): Rotation? {
        val key = uri.toString()
        if (!prefs.contains(key)) return null
        return Rotation(
            rotation = prefs.getFloat("$key.rotation", 0f),
            scale = prefs.getFloat("$key.scale", 1f),
            tx = prefs.getFloat("$key.tx", 0f),
            ty = prefs.getFloat("$key.ty", 0f),
            volume = prefs.getFloat("$key.volume", 1f)
        )
    }

    fun save(uri: Uri, rotation: Float, scale: Float, tx: Float, ty: Float, volume: Float) {
        val key = uri.toString()
        prefs.edit()
            .putFloat("$key.rotation", rotation)
            .putFloat("$key.scale", scale)
            .putFloat("$key.tx", tx)
            .putFloat("$key.ty", ty)
            .putFloat("$key.volume", volume)
            .apply()
    }

    fun clear(uri: Uri) {
        val key = uri.toString()
        prefs.edit()
            .remove("$key.rotation")
            .remove("$key.scale")
            .remove("$key.tx")
            .remove("$key.ty")
            .remove("$key.volume")
            .apply()
    }

    data class Rotation(val rotation: Float, val scale: Float, val tx: Float, val ty: Float, val volume: Float)
}
