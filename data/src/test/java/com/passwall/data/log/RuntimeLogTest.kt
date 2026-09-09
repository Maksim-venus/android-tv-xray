package com.passwall.data.log

import org.junit.Assert.assertEquals
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
}
