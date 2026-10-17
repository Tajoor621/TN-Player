package com.tplayer.mediaplayer.playback

import com.tplayer.mediaplayer.data.History
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded by retained files; a newer position for a file replaces its pending write. */
class OrderedHistory(
    scope: CoroutineScope,
    private val write: suspend (History) -> Unit,
    private val read: suspend (String) -> History?,
    private val clearDatabase: suspend () -> Unit,
    private val onFailure: (Exception) -> Unit = {}
) {
    private val pending = linkedMapOf<String, History>()
    private val gate = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    init {
        scope.launch {
            for (signal in wake) try { flush() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { onFailure(e) }
        }
    }
    fun record(value: History) {
        synchronized(pending) {
            pending.remove(value.uri); pending[value.uri] = value
            while (pending.size > 200) pending.remove(pending.keys.first())
        }
        wake.trySend(Unit)
    }
    private suspend fun drain() {
        // One snapshot keeps continuous playback events from starving a lookup.
        val batch = synchronized(pending) { pending.values.toList().also { pending.clear() } }
        for ((index, value) in batch.withIndex()) try { write(value) }
        catch (e: Exception) {
            synchronized(pending) {
                val recent = pending.toMap(); pending.clear()
                for (unwritten in batch.drop(index)) pending[unwritten.uri] = unwritten
                for ((uri, newer) in recent) { pending.remove(uri); pending[uri] = newer }
                while (pending.size > 200) pending.remove(pending.keys.first())
            }
            throw e
        }
    }
    suspend fun flush() = gate.withLock { drain() }
    suspend fun historyFor(uri: String): History? = gate.withLock {
        drain()
        // Events arriving during disk IO take precedence over the disk snapshot.
        val stored = read(uri)
        synchronized(pending) { pending[uri] }?.let { value ->
            value.copy(duration = value.duration.takeIf { it > 0 } ?: stored?.duration ?: 0,
                mimeType = value.mimeType.ifBlank { stored?.mimeType.orEmpty() })
        } ?: stored
    }
    suspend fun clear() = gate.withLock {
        clearDatabase()
        // A failed clear must not discard positions still waiting to be saved.
        synchronized(pending) { pending.clear() }
    }
}
