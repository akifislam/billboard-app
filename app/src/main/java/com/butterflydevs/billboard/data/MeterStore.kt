package com.butterflydevs.billboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.meterDataStore by preferencesDataStore(name = "meters")

class MeterStore(private val context: Context) {

    private object Keys {
        val METERS_JSON = stringPreferencesKey("meters_json")
    }

    private val gson = Gson()
    private val listType = object : TypeToken<List<MeterInfo>>() {}.type

    suspend fun getAll(): List<MeterInfo> {
        val json = context.meterDataStore.data.first()[Keys.METERS_JSON]
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addMeter(meter: MeterInfo) {
        save(getAll() + meter)
    }

    suspend fun removeMeter(meterNo: String) {
        save(getAll().filterNot { it.meter == meterNo })
    }

    private suspend fun save(list: List<MeterInfo>) {
        val json = gson.toJson(list)
        context.meterDataStore.edit { it[Keys.METERS_JSON] = json }
    }
}
