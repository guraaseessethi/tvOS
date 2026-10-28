package app.lumen

import android.app.Application

class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lumen.init(this)
    }
}
