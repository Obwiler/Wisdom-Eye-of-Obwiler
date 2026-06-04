package com.obwiler.weo.app

import android.app.Application
import com.obwiler.weo.camera.CameraHolder
import com.obwiler.weo.sensor.ImuCollector
import com.obwiler.weo.sensor.ImuProvider

class WEOApplication : Application() {

    lateinit var cameraHolder: CameraHolder
        private set

    lateinit var imuCollector: ImuProvider
        private set

    override fun onCreate() {
        super.onCreate()
        cameraHolder = CameraHolder(this)
        imuCollector = ImuCollector(this)
    }

    override fun onTerminate() {
        cameraHolder.release()
        super.onTerminate()
    }
}
