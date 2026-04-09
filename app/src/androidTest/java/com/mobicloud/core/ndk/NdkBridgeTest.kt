package com.mobicloud.core.ndk

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NdkBridgeTest {

    @Test
    fun testStringFromJNI() {
        val result = NdkBridge.stringFromJNI()
        assertEquals("Hello from C++ NDK!", result)
    }
}
