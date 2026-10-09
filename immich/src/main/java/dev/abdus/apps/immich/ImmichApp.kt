package dev.abdus.apps.immich

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.abdus.apps.immich.provider.ArtworkSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImmichApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply filter changes to Muzei once the user leaves the app, not on every toggle.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                owner.lifecycleScope.launch(Dispatchers.IO) {
                    ArtworkSync.syncIfFiltersChanged(this@ImmichApp)
                }
            }
        })
    }
}
