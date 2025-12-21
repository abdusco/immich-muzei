package dev.abdus.apps.immich.provider

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.apps.muzei.api.createChooseProviderIntent
import com.google.android.apps.muzei.api.isSelected
import com.google.android.apps.muzei.api.provider.ProviderContract

/**
 * Navigation helper for launching Muzei-related flows.
 */
class MuzeiNavigator(private val context: Context) {
    companion object {
        private const val TAG = "MuzeiNavigator"
    }

    fun isImmichActiveSource(): Boolean {
        return try {
            val client = ProviderContract.getProviderClient(context, ImmichAuthorities.IMMICH_AUTHORITY)
            val isActive = client.isSelected(context)
            Log.d(TAG, "Immich provider selected: $isActive")
            isActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Muzei source", e)
            false
        }
    }

    fun launchChooseMuzeiSource(context: Context) {
        val intents = listOf(
            createChooseProviderIntent(),
            context.packageManager.getLaunchIntentForPackage("net.nurik.roman.muzei")
        ).filterNotNull()

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Could not open Muzei with intent: $intent", e)
            }
        }

        Log.e(TAG, "Could not open Muzei app or provider chooser with any intent")
        Toast.makeText(context, "Could not open Muzei app.", Toast.LENGTH_LONG).show()
    }

    private fun createChooseProviderIntent(): Intent? {
        return try {
            ProviderContract
                .getProviderClient(context, ImmichAuthorities.IMMICH_AUTHORITY)
                .createChooseProviderIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating choose provider intent", e)
            null
        }
    }
}
