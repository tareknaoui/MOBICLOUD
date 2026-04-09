package com.mobicloud.core.ndk

import timber.log.Timber

object NdkBridge {
    init {
        try {
            System.loadLibrary("mobimath_lib")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load native library 'mobimath_lib'. JNI calls will fail.")
        }
    }

    external fun stringFromJNI(): String
}
