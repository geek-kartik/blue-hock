package com.client.blekotsdk.logging

/**
 * Internal logging delegate used throughout the SDK.
 */
object SdkLog : Logger {

    private var activeLogger: Logger = AndroidLogLogger()

    /**
     * Replaces the active logger with a custom implementation.
     */
    fun setLogger(logger: Logger) {
        activeLogger = logger
    }

    override fun d(tag: String, message: String) {
        activeLogger.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        activeLogger.i(tag, message)
    }

    override fun w(tag: String, message: String) {
        activeLogger.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        activeLogger.e(tag, message, throwable)
    }
}
