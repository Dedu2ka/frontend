package com.example.olo.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

fun decodeBase64ToBitmap(value: String): Bitmap? = runCatching {
    val encoded = value.substringAfter(',', value)
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

fun encodeAvatarFromUri(contentResolver: ContentResolver, uri: Uri): String {
    val mimeType = contentResolver.getType(uri)
    require(mimeType == null || mimeType.startsWith("image/", ignoreCase = true)) {
        "Можно выбрать только изображение"
    }

    val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val size = info.size
            val scale = minOf(1.0, 512.0 / max(size.width, size.height))
            decoder.setTargetSize(
                (size.width * scale).toInt().coerceAtLeast(1),
                (size.height * scale).toInt().coerceAtLeast(1)
            )
        }
    } else {
        decodeLegacyBitmap(contentResolver, uri)
    }

    val avatar = Bitmap.createScaledBitmap(bitmap, AVATAR_SIZE, AVATAR_SIZE, true)
    val output = ByteArrayOutputStream()
    check(avatar.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
        "Не удалось подготовить изображение"
    }
    return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

@Suppress("DEPRECATION")
private fun decodeLegacyBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Не удалось открыть изображение")

    val largestSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    val sampleSize = (largestSide / 512).coerceAtLeast(1)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Не удалось декодировать изображение")
}

private const val AVATAR_SIZE = 256
private const val JPEG_QUALITY = 80
