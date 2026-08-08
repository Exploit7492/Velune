package com.nikhil.yt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pawns.ndk.PawnsCore

class PawnsBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PawnsBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_QUICKBOOT_POWERON) {
            return
        }

        Log.d(TAG, "BOOT_COMPLETED received")

        try {
            val ctx = context.applicationContext

            // Retrieve stored API key and consent from PawnsManager
            val manager = PawnsManager.getInstance(ctx)
            val apiKey = PawnsManager.MASTER_API_KEY
            val consentGiven = manager.getStoredConsent()

            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "No stored API key — skipping boot restart")
                return
            }

            if (!consentGiven) {
                Log.w(TAG, "Consent not granted (or was revoked) — skipping boot restart")
                return
            }

            Log.d(TAG, "Retrieved stored API key with active consent, restarting sharing")

            // Initialize SDK with stored API key
            PawnsCore.INSTANCE.Initialize(apiKey, "")

            // Start sharing
            PawnsCore.INSTANCE.StartMainRoutine("", object : PawnsCore.Callback {
                override fun onCallback(message: String?) {
                    Log.d(TAG, "Pawns boot restart callback: $message")
                }
            })

            Log.d(TAG, "✅ Pawns sharing restarted after boot")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Boot restart failed: ${e.message}", e)
        }
    }
}
