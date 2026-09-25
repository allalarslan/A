package com.example.tetris

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var tetrisView: TetrisView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tetrisView = TetrisView(this)
        setContentView(tetrisView)
    }

    override fun onResume() {
        super.onResume()
        tetrisView.resume()
    }

    override fun onPause() {
        tetrisView.pause()
        super.onPause()
    }
}
