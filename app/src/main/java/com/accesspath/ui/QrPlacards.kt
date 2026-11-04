package com.accesspath.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.accesspath.R
import com.accesspath.domain.AppSnapshot
import com.accesspath.domain.LocationLinks
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.OutputStream

/** Writes an A4 PDF with one QR placard per place, six per page, ready to print and stick up. */
object QrPlacards {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val COLS = 2
    private const val ROWS = 3

    fun write(context: Context, state: AppSnapshot, out: OutputStream): Int {
        val places = LocationLinks.placesForPlacards(state.map)
        val doc = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; color = Color.BLACK }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; textAlign = Paint.Align.CENTER; color = Color.DKGRAY }
        val dark = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val border = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val hint = context.getString(R.string.qr_hint)
        val cellW = PAGE_W / COLS.toFloat()
        val cellH = PAGE_H / ROWS.toFloat()
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        try {
            places.chunked(COLS * ROWS).forEachIndexed { pageNo, chunk ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo + 1).create())
                val c = page.canvas
                chunk.forEachIndexed { i, node ->
                    val left = (i % COLS) * cellW
                    val top = (i / COLS) * cellH
                    c.drawRect(left + 6, top + 6, left + cellW - 6, top + cellH - 6, border)
                    val matrix = writer.encode(LocationLinks.forNode(node.id), BarcodeFormat.QR_CODE, 0, 0, hints)
                    val size = 170f
                    val module = size / matrix.width
                    val qx = left + (cellW - size) / 2
                    val qy = top + 22
                    for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                        if (matrix[x, y]) c.drawRect(qx + x * module, qy + y * module, qx + (x + 1) * module, qy + (y + 1) * module, dark)
                    }
                    val cx = left + cellW / 2
                    c.drawText(node.name.take(34), cx, qy + size + 22, title)
                    c.drawText(state.placeLabel(context, node).removePrefix(node.name).removePrefix(" · ").take(48), cx, qy + size + 38, small)
                    c.drawText(hint, cx, qy + size + 54, small)
                }
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return places.size
    }
}
