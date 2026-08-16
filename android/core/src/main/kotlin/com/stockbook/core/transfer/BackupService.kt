package com.stockbook.core.transfer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Reading and writing the backup file.
 *
 * Export writes a dated JSON file; import reads one back, **validates version
 * and shape before the owner is asked to confirm**, and only then replaces the
 * database.
 */
object BackupService {

    /**
     * Pretty-printed with sorted keys, matching what the iOS build writes — so a
     * file diffed or eyeballed on either phone looks the same, and a nulled
     * optional is simply absent rather than written as `null`, which is what
     * Swift's decoder expects of a missing value.
     */
    val json: Json = Json {
        prettyPrint = true
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(document: BackupDocument): String = json.encodeToString(document)

    /**
     * Decodes and validates. Version is checked *first*: a file from a future
     * build may decode cleanly into the current shape while meaning something
     * different, which is the failure mode worth being paranoid about when the
     * result is a destructive whole-database replace.
     */
    fun decode(text: String): BackupDocument {
        val probe = runCatching { json.parseToJsonElement(text) as JsonObject }
            .getOrElse { throw BackupError.NotStockbookData }
        val version = probe["version"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw BackupError.NotStockbookData
        if (version > BackupDocument.currentVersion) throw BackupError.NewerVersion(version)

        return runCatching { json.decodeFromString<BackupDocument>(text) }
            .getOrElse { throw BackupError.NotStockbookData }
    }

    /** A rough size for the file chip in Settings. The unit is `Strings`' business. */
    fun sizeInKilobytes(document: BackupDocument): Int {
        val bytes = runCatching { encode(document).toByteArray().size }.getOrDefault(0)
        return maxOf(1, (bytes / 1024.0).roundToInt())
    }
}
