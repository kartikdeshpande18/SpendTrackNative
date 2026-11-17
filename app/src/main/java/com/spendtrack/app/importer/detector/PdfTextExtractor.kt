package com.spendtrack.app.importer.detector

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

object PdfTextExtractor {
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context)
            isInitialized = true
        }
    }

    fun extractText(context: Context, uri: Uri): String? {
        init(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                var document: PDDocument? = null
                try {
                    document = PDDocument.load(inputStream)
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    stripper.getText(document)
                } finally {
                    document?.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
