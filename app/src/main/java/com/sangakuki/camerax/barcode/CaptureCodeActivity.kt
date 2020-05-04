package com.sangakuki.camerax.barcode

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentFactory
import com.sangakuki.camerax.barcode.extensions.FLAGS_FULLSCREEN
import com.sangakuki.camerax.barcode.extensions.IMMERSIVE_FLAG_TIMEOUT

class CaptureCodeActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_code)

        container = findViewById(R.id.fragment_container)
    }

    override fun onResume() {
        super.onResume()
        container.postDelayed(
            { container.systemUiVisibility = FLAGS_FULLSCREEN }, IMMERSIVE_FLAG_TIMEOUT
        )
    }
}