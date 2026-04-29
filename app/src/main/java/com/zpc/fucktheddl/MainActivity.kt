package com.zpc.fucktheddl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zpc.fucktheddl.schedule.StarterScheduleRepository
import com.zpc.fucktheddl.ui.FuckTheDdlApp
import com.zpc.fucktheddl.ui.theme.FuckTheDdlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialState = StarterScheduleRepository().loadInitialState()
        setContent {
            FuckTheDdlTheme {
                FuckTheDdlApp(initialState = initialState)
            }
        }
    }
}

