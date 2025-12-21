package dev.abdus.apps.immich.shortcuts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Invisible trampoline activity that immediately delegates to FavoriteReceiver
 * This avoids any visible UI flash
 */
class FavoriteShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sendBroadcast(Intent(FavoriteReceiver.ACTION_FAVORITE_CURRENT).apply {
            setPackage(packageName)
        })

        finish()
    }
}

