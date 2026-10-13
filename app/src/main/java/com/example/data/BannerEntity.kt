package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

/** Admin-managed home-screen banner. Stored in RTDB at banners/{id}. */
data class BannerEntity(
    val id: String = "",
    val imageUrl: String = "",
    val title: String = "",
    val subtitle: String = "",
    val buttonText: String = "",
    /** "none" | "app" | "collection" | "link" */
    val targetType: String = "none",
    val targetValue: String = "",
    val order: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
) : Serializable {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("imageUrl", imageUrl); put("title", title); put("subtitle", subtitle)
        put("buttonText", buttonText); put("targetType", targetType); put("targetValue", targetValue)
        put("order", order); put("isActive", isActive); put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject, fallbackId: String = ""): BannerEntity = BannerEntity(
            id = o.optString("id").ifBlank { fallbackId },
            imageUrl = o.optString("imageUrl"), title = o.optString("title"), subtitle = o.optString("subtitle"),
            buttonText = o.optString("buttonText"),
            targetType = o.optString("targetType").ifBlank { "none" }, targetValue = o.optString("targetValue"),
            order = o.optInt("order"), isActive = o.optBoolean("isActive", true), createdAt = o.optLong("createdAt")
        )

        fun listToJson(list: List<BannerEntity>): String =
            JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<BannerEntity> = try {
            if (json.isNullOrBlank()) emptyList() else {
                val a = JSONArray(json)
                (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
            }
        } catch (e: Exception) { emptyList() }
    }
}
