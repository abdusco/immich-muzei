package dev.abdus.apps.immich.shortcuts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Invisible trampoline activity that immediately delegates to FavoriteShortcutReceiver
 * This avoids any visible UI flash
 */
class FavoriteShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immediately send broadcast to the receiver and finish
        // This avoids any visible activity
        sendBroadcast(Intent(FavoriteShortcutReceiver.ACTION_FAVORITE).apply {
            setPackage(packageName)
        })

        finish()
    }
}

