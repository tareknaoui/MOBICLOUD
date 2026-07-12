package com.mobicloud.presentation.invite

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Génération QR pure (pas de scan) — zxing-core n'a aucune dépendance caméra/Android,
 * juste l'encodage texte → matrice de bits, qu'on convertit ici en [Bitmap] ARGB.
 */
object QrCodeGenerator {

    fun generate(content: String, sizePx: Int = 512): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
        bitmap
    }.getOrNull()

    private const val BLACK = android.graphics.Color.BLACK
    private const val WHITE = android.graphics.Color.WHITE
}
