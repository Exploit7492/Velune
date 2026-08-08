package com.nikhil.yt

import android.app.Application
import com.pawns.ndk.PawnsCore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // PawnsCore.init(this, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzZGsiOnRydWUsImV4cCI6MjEwMTU3Mzg5NSwianRpIjoiMDFLWkhBQTNRR1RUQzlLSktHVzdHUlNYVzkiLCJpYXQiOjE3ODYyMTM4OTUsInN1YiI6IjAxS0hCOFJaTk41SzIzVjU0VFdXMjZQS1I3In0.i6lfrMveuglFgWKVDEKLHwpp_GkqcUlmVGJ1_Fv9Gjk")   // replace with actual method and key
    }
}
