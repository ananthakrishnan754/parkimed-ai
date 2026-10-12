package com.parkimed.ai.camera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.parkimed.ai.R

/**
 * Pill-intake verification with CameraX.
 *
 * TODO (implementation task):
 *   - bind Preview + ImageAnalysis in onStart
 *   - run a lightweight on-device TFLite detector (MediaPipe) to decide
 *     whether a pill-hand is present
 *   - On positive confirmation: store a pill log row in Room with timestamp
 *     so adherence % can be computed.
 *
 * The scaffolding below wires the preview; detection+logging is the next task.
 */
class PillCaptureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pill_capture)
        findViewById<PreviewView>(R.id.previewView) // camera preview surface
        findViewById<android.widget.Button>(R.id.btnConfirmPill).setOnClickListener {
            Toast.makeText(this, "Pill intake logged (detector TODO)", Toast.LENGTH_SHORT).show()
        }
    }
}