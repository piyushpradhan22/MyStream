package com.mystream.app

import android.app.Application
import com.mystream.app.data.repository.SourcesRepository
import com.mystream.app.player.MyStreamPlayerManager

class MyStreamApplication : Application() {

    lateinit var sourcesRepository: SourcesRepository
        private set

    lateinit var playerManager: MyStreamPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        sourcesRepository = SourcesRepository(this)
        playerManager = MyStreamPlayerManager(this, sourcesRepository = sourcesRepository)
    }

    override fun onTerminate() {
        playerManager.release()
        super.onTerminate()
    }
}
