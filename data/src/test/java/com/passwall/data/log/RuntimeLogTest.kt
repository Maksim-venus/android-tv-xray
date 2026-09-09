package com.passwall.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeLogTest {
    @Before
    fun reset() {
        RuntimeLog.clear()
    }

    @Test
    fun newestFirstAndErrorFilter() {
        RuntimeLog.info("启动", "vpn")
        RuntimeLog.error("失败", "vpn")
        val all = RuntimeLog.snapshot(newestFirst = true)
        assertEquals("error", all.first().level)
        assertEquals("失败", all.first().message)
        assertEquals(1, RuntimeLog.snapshot(level = "error").size)
        assertEquals("失败", RuntimeLog.latestError()?.message)
    }

    @Test
    fun dropsOldestWhenFull() {
        repeat(RuntimeLog.CAPACITY + 5) { i -> RuntimeLog.info("m$i") }
        val items = RuntimeLog.snapshot(newestFirst = false)
        assertEquals(RuntimeLog.CAPACITY, items.size)
        assertEquals("m5", items.first().message)
        assertEquals("m${RuntimeLog.CAPACITY + 4}", items.last().message)
    }

    @Test
    fun dropsEntriesOlderThanSevenDaysOnWrite() {
        val now = System.currentTimeMillis()
        RuntimeLog.info("太旧", "vpn", at = now - RuntimeLog.RETENTION_MS - 1_000)
        RuntimeLog.info("还在", "vpn", at = now)
        val items = RuntimeLog.snapshot(newestFirst = false)
        assertEquals(1, items.size)
        assertEquals("还在", items.single().message)
    }

    @Test
    fun pruneRemovesExpiredAndKeepsRecent() {
        val now = System.currentTimeMillis()
        RuntimeLog.info("会过期", at = now)
        RuntimeLog.prune(now + RuntimeLog.RETENTION_MS + 1)
        assertTrue(RuntimeLog.snapshot().isEmpty())
        RuntimeLog.info("新的")
        RuntimeLog.prune()
        assertEquals(1, RuntimeLog.snapshot().size)
    }

    @Test
    fun latestErrorIgnoresExpired() {
        val now = System.currentTimeMillis()
        RuntimeLog.error("过期错误", at = now)
        RuntimeLog.prune(now + RuntimeLog.RETENTION_MS + 1)
        assertNull(RuntimeLog.latestError())
    }

    @Test
    fun clearStillDeletesEverything() {
        RuntimeLog.info("a")
        RuntimeLog.error("b")
        RuntimeLog.clear()
        assertTrue(RuntimeLog.snapshot().isEmpty())
        assertNull(RuntimeLog.latestError())
    }
}
