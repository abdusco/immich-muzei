package dev.abdus.apps.immich.provider

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.apps.muzei.api.createChooseProviderIntent
import com.google.android.apps.muzei.api.isSelected
import com.google.android.apps.muzei.api.provider.ProviderContract

private const val TAG = "Muzei"
private const val MUZEI_PACKAGE = "net.nurik.roman.muzei"

private fun Context.immichProviderClient() =
    ProviderContract.getProviderClient(this, ImmichArtProvider.AUTHORITY)

/** Whether Immich is the active Muzei source. */
fun Context.isImmichSelectedInMuzei(): Boolean = try {
    immichProviderClient().isSelected(this)
} catch (e: Exception) {
    Log.e(TAG, "Error checking Muzei source", e)
    false
}

/** Opens Muzei's source picker with Immich preselected, falling back to opening Muzei itself. */
fun Context.openMuzeiSourcePicker() {
    val intents = listOfNotNull(
        runCatching { immichProviderClient().createChooseProviderIntent() }.getOrNull(),
        packageManager.getLaunchIntentForPackage(MUZEI_PACKAGE)
    )
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (e: Exception) {
            Log.e(TAG, "Could not open Muzei with intent: $intent", e)
        }
    }
    Toast.makeText(this, "Could not open Muzei app.", Toast.LENGTH_LONG).show()
}
