package org.openmrs.chwsync.android

import android.app.Application
import org.openmrs.chwsync.android.di.AppContainer

class ChwSyncApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initialize()
    }
}
