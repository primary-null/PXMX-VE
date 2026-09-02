package com.pxmx.app.data

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<*>)?.mapNotNull { it as? String }?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class FakeEditor : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { key?.let { temp[it] = values } }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
        override fun remove(key: String?): SharedPreferences.Editor = apply { key?.let { removed.add(it) } }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) data.clear()
            removed.forEach { data.remove(it) }
            data.putAll(temp)
        }
    }
}
