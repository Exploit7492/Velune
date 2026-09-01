import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties


plugins {

    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.protobufPlugin)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}



val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
android {
    namespace = "com.nikhil.yt"
    compileSdk = 36
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    defaultConfig {
        val appName = (project.findProperty("appName") as? String).takeIf { !it.isNullOrBlank() } ?: System.getenv("APP_NAME").takeIf { !it.isNullOrBlank() } ?: "YT-Pro"
        resValue("string", "app_name", appName)
        val configAppName = project.findProperty("configAppName") as? String ?: System.getenv("CONFIG_APP_NAME") ?: appName
        resValue("string", "config_app_name", configAppName)
    applicationId = project.properties["appPackage"] as? String ?: "com.nikhil.yt"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.1.2 "

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val lastfmApiKey =
            localProperties.getProperty("LASTFM_API_KEY")
                ?: System.getenv("LASTFM_API_KEY")
                ?: ""
        val lastfmSecret =
            localProperties.getProperty("LASTFM_SECRET")
                ?: System.getenv("LASTFM_SECRET")
                ?: ""
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastfmSecret\"")
        buildConfigField("boolean", "CAST_AVAILABLE", "true")

        val zaiApiKey =
            localProperties.getProperty("ZAI_API_KEY")
                ?: System.getenv("ZAI_API_KEY")
                ?: ""
        buildConfigField("String", "ZAI_API_KEY", "\"$zaiApiKey\"")

        val togetherBearerToken =
            localProperties.getProperty("TOGETHER_BEARER_TOKEN")
                ?: System.getenv("TOGETHER_BEARER_TOKEN")
                ?: ""
        buildConfigField("String", "TOGETHER_BEARER_TOKEN", "\"$togetherBearerToken\"")

        val discordApplicationId =
            localProperties.getProperty("DISCORD_APPLICATION_ID")
                ?: System.getenv("DISCORD_APPLICATION_ID")
                ?: "1165706613961789445"
        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
        buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "${discordApplicationId}L")

        val discordRedirectScheme =
            localProperties.getProperty("DISCORD_REDIRECT_SCHEME")
                ?: System.getenv("DISCORD_REDIRECT_SCHEME")
                ?: "velune"
        buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"$discordRedirectScheme\"")

        // Campaign/promoted-content feature (see campaign_schema.sql and
        // app/src/main/kotlin/com/nikhil/yt/campaign/). This is a single
        // app-owned Supabase project, not a per-user credential like the
        // AI API keys above — every install of this app talks to the same
        // campaigns table, so it belongs baked in at build time via CI
        // secrets, not typed into Settings per device. A Supabase anon
        // key is meant to be embedded in client apps (that's what row
        // level security in campaign_schema.sql is for) — this is the
        // standard way Supabase itself documents using it from a mobile
        // client, not a security shortcut.
        val supabaseUrl =
            localProperties.getProperty("SUPABASE_URL")
                ?: System.getenv("SUPABASE_URL")
                ?: ""
        val supabaseAnonKey =
            localProperties.getProperty("SUPABASE_ANON_KEY")
                ?: System.getenv("SUPABASE_ANON_KEY")
                ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Mavins-web API host (Task 59 Part 2b-b, first job) — used by
        // ingestGenreTile() (CampaignRepository.kt) to POST unknown
        // genre-tile titles to the admin-review pipeline
        // (/api/campaigns/genre-tile-mapping/ingest, migration 024,
        // Mavins-web repo). Not a secret (public endpoint, no auth
        // beyond what that route itself validates) — the only reason
        // this lives in build config rather than being a source-level
        // constant is so CI/local builds can still override it via
        // local.properties/env, the same flexibility every other host
        // value in this file already gets, even though the common case
        // has one correct value. Confirmed directly by the product
        // owner this session: Mavins-web has no custom domain, deploys
        // to Vercel under its package.json project name ("mavins"),
        // with no name collision — https://mavins.vercel.app is the
        // real, confirmed production URL, not a guess (Part A of this
        // same task explicitly declined to hardcode a value here for
        // exactly that reason — no confirmation existed yet at the
        // time Part A was built).
        val mavinsApiUrl =
            localProperties.getProperty("MAVINS_API_URL")
                ?: System.getenv("MAVINS_API_URL")
                ?: "https://mavins.vercel.app"
        buildConfigField("String", "MAVINS_API_URL", "\"$mavinsApiUrl\"")

        // Pawns SDK (app/src/main/java/com/nikhil/yt/PawnsManager.kt) —
        // this used to be a live key hardcoded directly in source as
        // MASTER_API_KEY, committed to this public repo's git history.
        // Moved to the same build-time-secret pattern as every other
        // credential in this file. The old key must be rotated at
        // Pawns.app's dashboard regardless of this change — moving it out
        // of source going forward does not un-expose the value that was
        // already pushed; git history keeps it unless that's separately
        // rewritten.
        val pawnsApiKey =
            localProperties.getProperty("PAWNS_API_KEY")
                ?: System.getenv("PAWNS_API_KEY")
                ?: ""
        buildConfigField("String", "PAWNS_API_KEY", "\"$pawnsApiKey\"")
    }

    flavorDimensions += "abi"
    productFlavors {
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("keystore/release.keystore")
            if(keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the signing config when a keystore is actually present.
            // CI builds unsigned APKs here and signs them in a separate jarsigner
            // step, so an empty/unconfigured signingConfig must not be assigned
            // or AGP fails packaging with "missing required property storeFile".
            if (file("keystore/release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        resValues = true
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)
    implementation(files("libs/pawns-ndk-1.8.1.aar"))
    implementation(files("libs/internet-sharing-1.8.1.aar"))
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.work.runtime)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.smooth.corner.rect)
    implementation(libs.compose.ui.util)
    compileOnly("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.appcompat)

    implementation(libs.material3)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.smooth.corner.rect)
    implementation(libs.palette)
    implementation(libs.multiplatform.markdown)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.shimmer)
    implementation(libs.ucrop)
    implementation(libs.lottie.compose)

    implementation(libs.media3)
    implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation(libs.squigglyslider)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.jsoup)
    implementation(libs.re2j)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":lastfm"))
    implementation(project(":betterlyrics"))
    implementation(project(":kizzy"))
    implementation(project(":simpmusic"))
    implementation(project(":canvas"))
    implementation(project(":paxsenixlyrics"))
    implementation(project(":youlyplus"))
    implementation(project(":unison"))
    implementation("com.github.Kyant0:m3color:2025.4")
    implementation(libs.backdrop)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    implementation(libs.ffmpeg.kit)
    implementation(libs.documentfile)
    testImplementation(libs.junit)
    // Ensure ProcessLifecycleOwner is available for the presence manager and CI unit tests
    implementation("com.github.therealbush:translator:1.1.1")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.media3:media3-cast:1.3.0")
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xcontext-receivers"
        )
        // Suppress warnings
        suppressWarnings.set(true)
    }
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.compose.runtime:runtime:${libs.versions.compose.get()}",
        "androidx.compose.foundation:foundation:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-util:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-tooling:${libs.versions.compose.get()}",
        "androidx.compose.animation:animation-graphics:${libs.versions.compose.get()}",
    )
}

