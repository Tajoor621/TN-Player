package com.tplayer.mediaplayer

import com.tplayer.mediaplayer.data.History
import com.tplayer.mediaplayer.playback.OrderedHistory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OrderedHistoryTest {
    @Test fun immediateReopenWaitsForTheNewPositionAndRetainsOtherFiles() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val rows = java.util.concurrent.ConcurrentHashMap<String, History>()
        val writer = OrderedHistory(scope, { value -> entered.complete(Unit); release.await(); rows[value.uri] = value }, { rows[it] }, { rows.clear() })
        try {
            writer.record(History("content://one", "One", 12000, 90000))
            entered.await()
            writer.record(History("content://two", "Two", 5000, 90000))
            writer.record(History("content://one", "One", 18000, 90000))
            val reopened = async { writer.historyFor("content://one") }
            yield(); assertFalse(reopened.isCompleted)
            release.complete(Unit)
            assertEquals(18000L, withTimeout(5000) { reopened.await() }!!.position)
            writer.flush(); assertEquals(5000L, rows["content://two"]!!.position)
            writer.clear(); assertNull(writer.historyFor("content://one")); assertTrue(rows.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun failedWriteCanBeRetriedWithoutDiscardingANewerPosition() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failed = CompletableDeferred<Unit>(); val rows = java.util.concurrent.ConcurrentHashMap<String, History>()
        val first = java.util.concurrent.atomic.AtomicBoolean(true)
        val writer = OrderedHistory(scope, { value -> if (first.getAndSet(false)) throw java.io.IOException("full"); rows[value.uri] = value }, { rows[it] }, { rows.clear() }, { failed.complete(Unit) })
        try {
            writer.record(History("content://one", "One", 10000, 90000)); withTimeout(5000) { failed.await() }
            writer.record(History("content://one", "One", 22000, 90000))
            assertEquals(22000L, withTimeout(5000) { writer.historyFor("content://one") }!!.position)
        } finally { scope.cancel() }
    }
    @Test fun burstRetainsTheNewestPositionForEachFile() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rows = java.util.concurrent.ConcurrentHashMap<String, History>()
        val writer = OrderedHistory(scope, { rows[it.uri] = it }, { rows[it] }, { rows.clear() })
        try {
            repeat(1000) { n -> writer.record(History("content://file/${n % 100}", "File", n.toLong(), 90000)) }
            repeat(100) { n -> assertEquals((900 + n).toLong(), writer.historyFor("content://file/$n")!!.position) }
        } finally { scope.cancel() }
    }
}
