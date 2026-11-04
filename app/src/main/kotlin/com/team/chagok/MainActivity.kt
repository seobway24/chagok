package com.team.chagok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.team.chagok.feature.onair.OnAirRoute
import com.team.chagok.feature.onair.OnAirViewModel
import com.team.chagok.feature.onair.theme.ChagokTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ChagokApplication).container

        setContent {
            // 낮/밤은 차량 야간 모드를 따른다
            ChagokTheme {
                val onAir: OnAirViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { OnAirViewModel(
                            container.newShowEngine(),
                            container.manualSignals,
                            container.familyProfile,
                            container::forgetFamily,
                            container.captions.current,
                            container::savePerson,
                            container::removePerson,
                            container.aiStatus,
                            container::testAi,
                            container::saveAi,
                            container::clearAi,
                            container.voiceLibrary,
                            container.captions::clear,
                            asksNameByVoice = !BuildConfig.DEMO_ANSWERS,
                        ) }
                    },
                )
                OnAirRoute(viewModel = onAir, showDebugPanel = BuildConfig.DEBUG || BuildConfig.DEMO_MEMORY)
            }
        }
    }
}
