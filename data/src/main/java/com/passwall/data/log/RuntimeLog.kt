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
 */
object RuntimeLog {
    const val CAPACITY = 300

    private val lock = Any()
    private val buf = ArrayDeque<RuntimeLogEntry>(CAPACITY)
    private val seq = AtomicLong(0)

    fun info(message: String, source: String = "") = add("info", message, source)
    fun warn(message: String, source: String = "") = add("warn", message, source)
    fun error(message: String, source: String = "") = add("error", message, source)

    fun add(level: String, message: String, source: String = "") {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val entry = RuntimeLogEntry(
            id = seq.incrementAndGet(),
            at = System.currentTimeMillis(),
            level = level.lowercase(),
            message = trimmed,
            source = source,
        )
        synchronized(lock) {
            if (buf.size >= CAPACITY) buf.removeFirst()
            buf.addLast(entry)
        }
    }

    fun snapshot(newestFirst: Boolean = true, level: String? = null): List<RuntimeLogEntry> {
        val copy = synchronized(lock) { buf.toList() }
        val filtered = if (level.isNullOrBlank()) copy else copy.filter { it.level == level.lowercase() }
        return if (newestFirst) filtered.asReversed() else filtered
    }

    fun latestError(): RuntimeLogEntry? = synchronized(lock) {
        buf.lastOrNull { it.level == "error" }
    }

    fun clear() {
        synchronized(lock) { buf.clear() }
    }
}
