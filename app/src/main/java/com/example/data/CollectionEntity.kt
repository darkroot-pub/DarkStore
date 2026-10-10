package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/**
 * An admin-curated list of apps ("Best Nepali apps", "Student tools"…) shown as a card on the
 * Apps tab. Stored in RTDB at collections/{id}.
 */
data class CollectionEntity(
    val id: String = "",
    val title: String = "",
    val titleNe: String = "",
    val titleHi: String = "",
    val subtitle: String = "",
    val subtitleNe: String = "",
    val subtitleHi: String = "",
    val color: String = "#10B981",
    val appIds: List<String> = emptyList(),
    val order: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
) : Serializable {

    fun titleFor(lang: String): String = when (lang) {
        "ne" -> titleNe.ifBlank { title }
        "hi" -> titleHi.ifBlank { title }
        else -> title
    }

    fun subtitleFor(lang: String): String = when (lang) {
        "ne" -> subtitleNe.ifBlank { subtitle }
        "hi" -> subtitleHi.ifBlank { subtitle }
        else -> subtitle
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("titleNe", titleNe); put("titleHi", titleHi)
        put("subtitle", subtitle); put("subtitleNe", subtitleNe); put("subtitleHi", subtitleHi)
        put("color", color); put("appIds", JSONArray(appIds))
        put("order", order); put("isActive", isActive); put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject, fallbackId: String = ""): CollectionEntity {
            val ids = mutableListOf<String>()
            // RTDB returns a JSON array for 0..n keys, but an object if keys have gaps.
            when (val raw = o.opt("appIds")) {
                is JSONArray -> for (i in 0 until raw.length()) raw.optString(i).takeIf { it.isNotBlank() && it != "null" }?.let(ids::add)
                is JSONObject -> raw.keys().forEach { k -> raw.optString(k).takeIf { it.isNotBlank() }?.let(ids::add) }
            }
            return CollectionEntity(
                id = o.optString("id").ifBlank { fallbackId },
                title = o.optString("title"), titleNe = o.optString("titleNe"), titleHi = o.optString("titleHi"),
                subtitle = o.optString("subtitle"), subtitleNe = o.optString("subtitleNe"), subtitleHi = o.optString("subtitleHi"),
                color = o.optString("color").ifBlank { "#10B981" },
                appIds = ids, order = o.optInt("order"),
                isActive = o.optBoolean("isActive", true), createdAt = o.optLong("createdAt")
            )
        }

        fun listToJson(list: List<CollectionEntity>): String =
            JSONArray().also { arr -> list.forEach { arr.put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<CollectionEntity> = try {
            if (json.isNullOrBlank()) emptyList() else {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }
        } catch (e: Exception) { emptyList() }
    }
}
