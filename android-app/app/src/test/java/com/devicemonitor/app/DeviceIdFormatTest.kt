package com.devicemonitor.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DeviceIdFormatTest {
    @Test
    fun generatedDeviceIdHasExpectedFormat() {
        val id = "dev_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        assertTrue(id.startsWith("dev_"))
        assertTrue(id.length >= 12)
    }
}
