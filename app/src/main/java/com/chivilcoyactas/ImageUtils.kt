package com.chivilcoyactas

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun compressBitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    // Esta función te va a salvar la vida con los 2GB de RAM
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int = 1024): Bitmap {
        val aspectRatio: Float = bitmap.width.toFloat() / bitmap.height.toFloat()
        val width = maxWidth
        val height = Math.round(width / aspectRatio)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}