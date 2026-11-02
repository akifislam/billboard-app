package com.butterflydevs.billboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.monthlyUsageDataStore by preferencesDataStore(name = "monthly_usage_cache")

const val MONTHLY_USAGE_TTL_MS = 6 * 60 * 60 * 1000L

data class MonthlyUsageCacheEntry(
    val rows: List<MonthlyUsageRow>,
    val fetchedAt: Long
)

class MonthlyUsageCache(private val context: Context) {

    private object Keys {
        val CACHE_JSON = stringPreferencesKey("cache_json")
    }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, MonthlyUsageCacheEntry>>() {}.type

    private suspend fun readAll(): Map<String, MonthlyUsageCacheEntry> {
        val json = context.monthlyUsageDataStore.data.first()[Keys.CACHE_JSON]
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            gson.fromJson(json, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun get(meterNo: String): MonthlyUsageCacheEntry? = readAll()[meterNo]

    suspend fun put(meterNo: String, rows: List<MonthlyUsageRow>, fetchedAt: Long) {
        val updated = readAll() + (meterNo to MonthlyUsageCacheEntry(rows, fetchedAt))
        context.monthlyUsageDataStore.edit { it[Keys.CACHE_JSON] = gson.toJson(updated) }
    }

    suspend fun remove(meterNo: String) {
        val updated = readAll() - meterNo
        context.monthlyUsageDataStore.edit { it[Keys.CACHE_JSON] = gson.toJson(updated) }
    }
}
