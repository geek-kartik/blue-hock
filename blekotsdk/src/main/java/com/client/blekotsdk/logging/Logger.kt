package com.client.blekotsdk.logging

/**
 * Logging component used throughout the SDK. Consumers can replace the
 * default implementation with their own logger (e.g. Timber, Logback,
 * crash-reporting loggers).
 */
interface Logger {

    /** Log a debug message. */
    fun d(tag: String, message: String)

    /** Log an informational message. */
    fun i(tag: String, message: String)

    /** Log a warning message. */
    fun w(tag: String, message: String)

    /** Log an error message with optional stacktrace. */
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
