package io.github.morningtv.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Sets one config value. Protected by android.permission.DUMP in the manifest,
 * so only the adb shell (scripts/install.sh) can send it. Values are never logged.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val key = intent.getStringExtra("key")
        val value = intent.getStringExtra("value")
        val store = ConfigStore(context.getSharedPreferences(ConfigStore.PREFS_NAME, Context.MODE_PRIVATE))
        resultData = if (key != null && value != null && store.set(key, value)) "ok:$key" else "ignored"
    }

    companion object {
        const val ACTION = "io.github.morningtv.SET_CONFIG"
    }
}
