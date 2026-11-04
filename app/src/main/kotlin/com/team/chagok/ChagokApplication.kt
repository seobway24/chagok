package com.team.chagok

import android.app.Application
import com.team.chagok.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChagokApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { container.restoreMemory() }
        if (BuildConfig.DEBUG) {
            val probes = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            probes.launch { container.probeRemote() }
            probes.launch { container.probePleosLlm() }
            probes.launch { container.probeNavi() }
        }
    }
}
