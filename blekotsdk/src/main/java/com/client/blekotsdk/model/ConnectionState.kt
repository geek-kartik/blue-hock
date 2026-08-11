package com.client.blekotsdk.model

/**
 * High level connection state of a GATT link.
 */
enum class ConnectionState {
    /** Not connected to any device. */
    DISCONNECTED,

    /** Actively attempting to establish a connection. */
    CONNECTING,

    /** Connected and services discovered. */
    CONNECTED
}
