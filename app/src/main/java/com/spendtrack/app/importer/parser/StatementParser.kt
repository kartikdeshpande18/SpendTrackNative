package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.NormalizedTransaction

interface StatementParser {
    val identifier: String
    fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction>
}
