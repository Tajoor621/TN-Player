package com.tplayer.mediaplayer.data

import org.json.JSONArray

internal object FolderExclusions {
    const val KEY = "excludedFolders"
    const val LIMIT = 256

    private fun valid(folder: IndexedFolder): Boolean = folder.volume.length in 1..128 &&
        folder.volume.none { it.code < 32 || it == '/' || it == '\\' } &&
        folder.path.length <= 1024 && folder.path.none { it.code < 32 || it == '\\' } &&
        folder.path.split('/').none { it == "." || it == ".." }

    fun encode(folders: Set<IndexedFolder>): String {
        require(folders.size <= LIMIT && folders.all(::valid))
        return JSONArray().apply {
            folders.sortedWith(compareBy({ it.volume }, { it.path })).forEach {
                put(JSONArray().put(it.volume).put(it.path.trim('/') + if (it.path.isBlank()) "" else "/"))
            }
        }.toString()
    }

    fun decode(value: String): Set<IndexedFolder> {
        require(value.length <= 600_000)
        val rows = JSONArray(value)
        require(rows.length() <= LIMIT)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONArray(i)
            require(row.length() == 2 && row.get(0) is String && row.get(1) is String)
            val path = row.getString(1).trim('/')
            IndexedFolder(row.getString(0), if (path.isEmpty()) "" else "$path/").also { require(valid(it)) }
        }.toSet()
    }

    fun matches(folder: IndexedFolder, excluded: Set<IndexedFolder>): Sequence<IndexedFolder> =
        generateSequence(folder) { it.parent() }.filter { it in excluded }
}
