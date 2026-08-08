package com.nikhil.yt

import android.app.Application
import com.pawns.ndk.PawnsCore

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzZGsiOnRydWUsImV4cCI6MjEwMTU3Mzg5NSwianRpIjoiMDFLWkhBQTNRR1RUQzlLSktHVzdHUlNYVzkiLCJpYXQiOjE3ODYyMTM4OTUsInN1YiI6IjAxS0hCOFJaTk41SzIzVjU0VFdXMjZQS1I3In0.i6lfrMveuglFgWKVDEKLHwpp_GkqcUlmVGJ1_Fv9Gjk"

        // Initialize with API key and custom text (assumed notification text)
//         PawnsCore.INSTANCE.Initialize(apiKey, "🎵streaming music 🎶")

        // Callback with a single method
        val callback = object : PawnsCore.Callback {
            override fun onCallback(message: String?) {
                // Optional: handle status updates from the SDK
                // message might contain status or error info
            }
        }

        // Start main routine – first parameter might be notification text
//         PawnsCore.INSTANCE.StartMainRoutine("🎵streaming music 🎶", callback)
    }
}
