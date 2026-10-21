package com.sift.photos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sift.photos.ui.SiftApp
import com.sift.photos.ui.SiftViewModel

class MainActivity : ComponentActivity() {
    private val vm: SiftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SiftApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // Access may have changed in Settings or the photo picker while we were away.
        vm.refresh()
    }
}
