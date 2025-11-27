package dev.abdus.apps.immich.shortcuts

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Transparent activity that shows a toast message and immediately finishes
 */
class ToastActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MESSAGE = "message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent.getStringExtra(EXTRA_MESSAGE)
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}

