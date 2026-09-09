package com.passwall.data.log

import kotlinx.serialization.Serializable
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class RuntimeLogEntry(
    val id: Long,
    val at: Long,
    val level: String,
    val message: String,
    val source: String = "",
)

/**
 * Process-wide ring buffer for phone-browser troubleshooting.
 * Newest entries are returned first from [snapshot].
 *
 * Retention: entries older than [RETENTION_DAYS] days are dropped on write,
 * snapshot, and [prune]. [clear] still deletes everything.
 */
object RuntimeLog {
    const val CAPACITY = 300
    const val RETENTION_DAYS = 7
    const val RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    private val lock = Any()
    private val buf = ArrayDeque<RuntimeLogEntry>(CAPACITY)
    private val seq = AtomicLong(0)

    fun info(message: String, source: String = "", at: Long = System.currentTimeMillis()) =
        add("info", message, source, at)

    fun warn(message: String, source: String = "", at: Long = System.currentTimeMillis()) =
        add("warn", message, source, at)

    fun error(message: String, source: String = "", at: Long = System.currentTimeMillis()) =
        add("error", message, source, at)

    fun add(level: String, message: String, source: String = "", at: Long = System.currentTimeMillis()) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val entry = RuntimeLogEntry(
            id = seq.incrementAndGet(),
            at = at,
            level = level.lowercase(),
            message = trimmed,
            source = source,
        )
        synchronized(lock) {
            val now = System.currentTimeMillis()
            pruneLocked(now)
            if (at < now - RETENTION_MS) return
            if (buf.size >= CAPACITY) buf.removeFirst()
            buf.addLast(entry)
        }
    }

    fun prune(now: Long = System.currentTimeMillis()) {
        synchronized(lock) { pruneLocked(now) }
    }

    fun snapshot(newestFirst: Boolean = true, level: String? = null): List<RuntimeLogEntry> {
        val copy = synchronized(lock) {
            pruneLocked(System.currentTimeMillis())
            buf.toList()
        }
        val filtered = if (level.isNullOrBlank()) copy else copy.filter { it.level == level.lowercase() }
        return if (newestFirst) filtered.asReversed() else filtered
    }

    fun latestError(): RuntimeLogEntry? = synchronized(lock) {
        pruneLocked(System.currentTimeMillis())
        buf.lastOrNull { it.level == "error" }
    }

    fun clear() {
        synchronized(lock) { buf.clear() }
    }

    private fun pruneLocked(now: Long) {
        val cutoff = now - RETENTION_MS
        while (buf.isNotEmpty() && buf.first().at < cutoff) {
            buf.removeFirst()
        }
    }
}
