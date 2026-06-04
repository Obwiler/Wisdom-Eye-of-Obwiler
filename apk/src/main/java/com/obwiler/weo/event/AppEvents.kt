package com.obwiler.weo.event

import kotlinx.coroutines.channels.Channel

object AppEvents {
    val shutterRequest = Channel<Unit>(Channel.CONFLATED)
    val configUpdated = Channel<Unit>(Channel.CONFLATED)
    val cameraPermissionGranted = Channel<Unit>(Channel.CONFLATED)
}