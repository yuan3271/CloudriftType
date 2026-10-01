package com.yuan3271.cloudrift

import android.app.Application
import com.yuan3271.cloudrift.data.AppGraph

class CloudriftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
