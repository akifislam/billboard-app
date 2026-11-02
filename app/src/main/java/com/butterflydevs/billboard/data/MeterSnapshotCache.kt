package com.butterflydevs.billboard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.butterflydevs.billboard.viewmodel.MeterCardState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.meterSnapshotDataStore by preferencesDataStore(name = "meter_snapshots")

/**
 * The last successfully fetched card for each meter.
 *
 * Balances were previously only ever held in memory, so every launch started blank and an offline
 * launch had nothing at all to show. Persisting the last good result means the dashboard opens on
 * real numbers immediately and still shows them when there is no network — clearly marked as the
 * last saved values rather than live ones.
 */
class MeterSnapshotCache(private val context: Context) {

    private object Keys {
        val SNAPSHOTS_JSON = stringPreferencesKey("snapshots_json")
    }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, MeterCardState>>() {}.type

    suspend fun getAll(): Map<String, MeterCardState> =
        parse(context.meterSnapshotDataStore.data.first()[Keys.SNAPSHOTS_JSON])

    suspend fun put(state: MeterCardState) {
        // Never persist the transient flags — a cached card is by definition settled and error-free.
        val clean = state.copy(isLoading = false, error = null)
        update { it + (state.meter to clean) }
    }

    suspend fun remove(meterNo: String) {
        update { it - meterNo }
    }

    /**
     * Re-reads inside the transaction rather than around it. A refresh fans out across every meter
     * at once, so a read-modify-write done outside `edit` would let concurrent saves overwrite each
     * other and silently drop meters from the cache. DataStore serialises `edit` blocks, which
     * makes this read-then-write atomic.
     */
    private suspend fun update(transform: (Map<String, MeterCardState>) -> Map<String, MeterCardState>) {
        try {
            context.meterSnapshotDataStore.edit { prefs ->
                prefs[Keys.SNAPSHOTS_JSON] = gson.toJson(transform(parse(prefs[Keys.SNAPSHOTS_JSON])))
            }
        } catch (e: Exception) {
            // Caching is best-effort; failing to persist must never break a refresh.
        }
    }

    private fun parse(json: String?): Map<String, MeterCardState> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            gson.fromJson(json, mapType) ?: emptyMap()
        } catch (e: Exception) {
            // A snapshot written by an older build that no longer parses is not worth crashing
            // over; the meters simply reload from the network as they always did.
            emptyMap()
        }
    }
}
