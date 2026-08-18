package com.example.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

object PaginationCache {
    private val memoryCache = mutableMapOf<String, List<AppEntity>>()
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    
    // Memory cache limit (LRU-like cache retention)
    private val memoryLRUList = mutableListOf<String>()
    private const val MAX_MEMORY_PAGES = 5 // maximum categories in RAM

    fun getApps(context: Context, key: String): List<AppEntity>? {
        // Check memory cache first
        if (memoryCache.containsKey(key)) {
            Log.d("PaginationCache", "Memory cache hit for key: $key")
            // Update LRU
            memoryLRUList.remove(key)
            memoryLRUList.add(key)
            return memoryCache[key]
        }
        
        // If not in memory, restore from disk cache
        val diskApps = loadFromDisk(context, key)
        if (diskApps != null) {
            Log.d("PaginationCache", "Disk cache hit for key: $key. Restoring to memory.")
            putInMemory(key, diskApps)
            return diskApps
        }
        
        return null
    }

    fun saveApps(context: Context, key: String, apps: List<AppEntity>) {
        putInMemory(key, apps)
        saveToDisk(context, key, apps)
    }

    private fun putInMemory(key: String, apps: List<AppEntity>) {
        memoryCache[key] = apps
        memoryLRUList.remove(key)
        memoryLRUList.add(key)
        
        // Evict oldest from RAM if limit reached
        if (memoryCache.size > MAX_MEMORY_PAGES) {
            val oldestKey = memoryLRUList.removeAt(0)
            memoryCache.remove(oldestKey)
            Log.d("PaginationCache", "Evicted key from RAM (Smart Memory Management): $oldestKey")
        }
    }

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int>? {
        return scrollPositions[key]
    }

    private fun getDiskFile(context: Context, key: String): File {
        val cacheDir = File(context.cacheDir, "pagination_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, "cache_${key.hashCode()}.json")
    }

    private fun saveToDisk(context: Context, key: String, apps: List<AppEntity>) {
        try {
            val file = getDiskFile(context, key)
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, AppEntity::class.java)
            val adapter = moshi.adapter<List<AppEntity>>(type)
            val json = adapter.toJson(apps)
            file.writeText(json)
            Log.d("PaginationCache", "Saved key to disk cache: $key")
        } catch (e: Exception) {
            Log.e("PaginationCache", "Failed to save to disk cache: $key", e)
        }
    }

    private fun loadFromDisk(context: Context, key: String): List<AppEntity>? {
        try {
            val file = getDiskFile(context, key)
            if (file.exists()) {
                val json = file.readText()
                val moshi = Moshi.Builder().build()
                val type = Types.newParameterizedType(List::class.java, AppEntity::class.java)
                val adapter = moshi.adapter<List<AppEntity>>(type)
                return adapter.fromJson(json)
            }
        } catch (e: Exception) {
            Log.e("PaginationCache", "Failed to load from disk cache: $key", e)
        }
        return null
    }
}
