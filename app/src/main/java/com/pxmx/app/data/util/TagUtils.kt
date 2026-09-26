package com.pxmx.app.data.util

object TagUtils {
    /**
     * Splits a raw Proxmox tag string by ';' into clean trimmed tags, omitting empty entries.
     */
    fun splitTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Joins a list of tags with ';' separators, omitting blanks and deduplicating.
     */
    fun joinTags(tags: List<String>): String {
        return tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(";")
    }

    /**
     * A tag is a short label: trimmed, non-empty, and does not contain ';'.
     */
    fun isValidTag(tag: String): Boolean {
        val trimmed = tag.trim()
        return trimmed.isNotEmpty() && !trimmed.contains(';')
    }

    /**
     * Adds a tag to the existing tag string if valid, returning the joined tag string.
     */
    fun addTag(raw: String?, newTag: String): String {
        val trimmed = newTag.trim()
        if (!isValidTag(trimmed)) return raw?.trim().orEmpty()
        val current = splitTags(raw)
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return joinTags(current)
        return joinTags(current + trimmed)
    }

    /**
     * Removes a tag from the existing tag string, returning the joined tag string.
     */
    fun removeTag(raw: String?, tagToRemove: String): String {
        val trimmed = tagToRemove.trim()
        val current = splitTags(raw)
        return joinTags(current.filter { !it.equals(trimmed, ignoreCase = true) })
    }
}
