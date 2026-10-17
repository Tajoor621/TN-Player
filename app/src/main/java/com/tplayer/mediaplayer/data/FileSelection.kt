package com.tplayer.mediaplayer.data

/** A pending selection is scoped to one browser location; no destructive action runs automatically. */
class FileSelection {
    companion object { const val LIMIT = 1000 }
    private var scope = ""
    var version = 0L; private set
    var active = false; private set
    private val values = linkedMapOf<String, LibraryItem>()
    val items get() = values.values.toList()
    val size get() = values.size
    fun contains(uri: String) = values.containsKey(uri)
    fun enter() { active = true; version++ }
    fun scope(value: String) { if (scope != value) { clear(); scope = value } }
    fun toggle(item: LibraryItem) {
        require(item.indexedFolder == null && item.detail != "Folder") { "Select files, not folders." }
        active = true; version++
        if (values.remove(item.uri) == null) { require(values.size < LIMIT) { "Select at most $LIMIT files per action." }; values[item.uri] = item }
    }
    fun all(items: List<LibraryItem>) {
        val files = items.filter { it.indexedFolder == null && it.detail != "Folder" }.distinctBy { it.uri }
        require(files.size <= LIMIT) { "More than $LIMIT files match. Narrow the search or select a smaller folder." }
        version++; values.clear(); files.forEach { values[it.uri] = it }; active = true
    }
    fun remove(uris: Collection<String>) { version++; uris.forEach(values::remove) }
    fun clear() { version++; values.clear(); active = false }
}
