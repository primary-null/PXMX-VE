package com.pxmx.app.data.session

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.WeakHashMap

/**
 * Synchronous [SharedPreferences] facade backed by an in-memory snapshot and
 * an encrypted [DataStore] persistence layer.
 *
 * Updates to memory are immediate. [apply] persists encrypted off-thread,
 * while [commit] persists synchronously.
 */
class VaultPreferences(
    private val dataStore: DataStore<VaultPayload>,
    initialPayload: VaultPayload,
    private val persistScope: CoroutineScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob()),
) : SharedPreferences {

    private val lock = Any()
    private var inMemory: VaultPayload = initialPayload
    private val listeners = Collections.newSetFromMap(WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>())

    override fun getAll(): MutableMap<String, Any?> = synchronized(lock) {
        val map = HashMap<String, Any?>()
        map.putAll(inMemory.strings)
        map.putAll(inMemory.booleans)
        map.putAll(inMemory.ints)
        map.putAll(inMemory.longs)
        map.putAll(inMemory.floats)
        map.putAll(inMemory.stringSets)
        map
    }

    override fun getString(key: String?, defValue: String?): String? = synchronized(lock) {
        if (key == null) return defValue
        inMemory.strings[key] ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(lock) {
        if (key == null) return defValues
        inMemory.stringSets[key]?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) {
        if (key == null) return defValue
        inMemory.ints[key] ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) {
        if (key == null) return defValue
        inMemory.longs[key] ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) {
        if (key == null) return defValue
        inMemory.floats[key] ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) {
        if (key == null) return defValue
        inMemory.booleans[key] ?: defValue
    }

    override fun contains(key: String?): Boolean = synchronized(lock) {
        if (key == null) return false
        inMemory.strings.containsKey(key) ||
            inMemory.booleans.containsKey(key) ||
            inMemory.ints.containsKey(key) ||
            inMemory.longs.containsKey(key) ||
            inMemory.floats.containsKey(key) ||
            inMemory.stringSets.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = VaultEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) {
            synchronized(listeners) {
                listeners.add(listener)
            }
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) {
            synchronized(listeners) {
                listeners.remove(listener)
            }
        }
    }

    private object DeletedSentinel

    inner class VaultEditor : SharedPreferences.Editor {
        private val modifications = HashMap<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                if (value == null) {
                    modifications[key] = DeletedSentinel
                } else {
                    modifications[key] = value
                }
            }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) {
                if (values == null) {
                    modifications[key] = DeletedSentinel
                } else {
                    modifications[key] = values.toSet()
                }
            }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) modifications[key] = DeletedSentinel
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        private fun applyModificationsToMemory(): Pair<VaultPayload, List<String>> = synchronized(lock) {
            val base = if (clearRequested) VaultPayload() else inMemory

            val strings = base.strings.toMutableMap()
            val booleans = base.booleans.toMutableMap()
            val ints = base.ints.toMutableMap()
            val longs = base.longs.toMutableMap()
            val floats = base.floats.toMutableMap()
            val stringSets = base.stringSets.toMutableMap()

            fun removeFromAll(k: String) {
                strings.remove(k)
                booleans.remove(k)
                ints.remove(k)
                longs.remove(k)
                floats.remove(k)
                stringSets.remove(k)
            }

            for ((k, v) in modifications) {
                removeFromAll(k)
                when (v) {
                    is String -> strings[k] = v
                    is Boolean -> booleans[k] = v
                    is Int -> ints[k] = v
                    is Long -> longs[k] = v
                    is Float -> floats[k] = v
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        stringSets[k] = (v as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    }
                    DeletedSentinel -> {
                        // Already removed from all
                    }
                }
            }

            val newPayload = VaultPayload(
                strings = strings,
                booleans = booleans,
                ints = ints,
                longs = longs,
                floats = floats,
                stringSets = stringSets,
            )
            inMemory = newPayload

            val keysToNotify = if (clearRequested) {
                (base.strings.keys + base.booleans.keys + base.ints.keys + base.longs.keys + base.floats.keys + base.stringSets.keys + modifications.keys).distinct()
            } else {
                modifications.keys.toList()
            }

            modifications.clear()
            clearRequested = false

            newPayload to keysToNotify
        }

        private fun notifyListeners(keys: List<String>) {
            if (keys.isEmpty()) return
            val currentListeners = synchronized(listeners) { listeners.toList() }
            for (listener in currentListeners) {
                for (key in keys) {
                    listener.onSharedPreferenceChanged(this@VaultPreferences, key)
                }
            }
        }

        override fun commit(): Boolean {
            val (snapshot, changedKeys) = applyModificationsToMemory()
            notifyListeners(changedKeys)
            return try {
                runBlocking {
                    dataStore.updateData { snapshot }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            }
        }

        override fun apply() {
            val (snapshot, changedKeys) = applyModificationsToMemory()
            notifyListeners(changedKeys)
            persistScope.launch {
                try {
                    dataStore.updateData { snapshot }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Silently drop according to SharedPreferences.apply contract
                }
            }
        }
    }
}
