package com.aika.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: GameViewModel = viewModel()
            when (vm.screen) {
                Screen.MENU -> MenuScreen(vm)
                Screen.LEVELS -> LevelsScreen(vm)
                Screen.GAME -> GameScreen(vm)
            }
        }
    }

    override fun onStart() { super.onStart(); Music.start() }
    override fun onStop() { super.onStop(); Music.pause() }
}
