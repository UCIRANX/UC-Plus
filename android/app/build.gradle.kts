import com.android.build.api.variant.FilterConfiguration

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// One signing key for every build. The keystore is generated on the first build and then
// reused, so each new APK is signed by the same key and installs over the previous one.
// Set UC_PLUS_KEYSTORE_PASSWORD (and optionally UC_PLUS_KEY_PASSWORD) in CI to keep the
// password out of this file; the keystore itself is git-ignored.
val releaseKeystoreDir = rootProject.file("keystore")
val releaseKeystoreFile = releaseKeystoreDir.resolve("uc-plus-release.jks")
val releaseKeyAlias = "ucplus"
val releaseStorePassword: String = System.getenv("UC_PLUS_KEYSTORE_PASSWORD") ?: "ucplus-release-store"
val releaseKeyPassword: String = System.getenv("UC_PLUS_KEY_PASSWORD") ?: releaseStorePassword

val ensureReleaseKeystore by tasks.registering {
    outputs.file(releaseKeystoreFile)
    doLast {
        if (releaseKeystoreFile.exists()) return@doLast
        releaseKeystoreDir.mkdirs()
        val keytool = File(System.getProperty("java.home"), "bin/keytool").takeIf { it.exists() }?.path ?: "keytool"
        val process = ProcessBuilder(
            keytool, "-genkeypair", "-v",
            "-keystore", releaseKeystoreFile.path,
            "-storetype", "PKCS12",
            "-storepass", releaseStorePassword,
            "-keypass", releaseKeyPassword,
            "-alias", releaseKeyAlias,
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=UC Plus, O=UC Plus, C=IR"
        ).inheritIO().start()
        check(process.waitFor() == 0) { "keytool could not create $releaseKeystoreFile" }
    }
}

// The sing-box extended core (libsingbox.so) and libhev-socks5-tunnel.so are committed under
// src/main/jniLibs for these ABIs only. Shipping any other ABI produces an APK that installs
// but can never start a tunnel, so the split and the packaged ABIs are pinned to this list.
val supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
// CI sets GITHUB_RUN_NUMBER so every published build has a higher versionCode than the last one.
val baseVersionCode = 1000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
val lumenVersionName = "1.5.2"
// AGP does not derive per-output version codes; without an offset every split APK would share one.
val abiVersionCodeOffsets = mapOf(
    "armeabi-v7a" to 1,
    "x86" to 2,
    "arm64-v8a" to 3,
    "x86_64" to 4
)

android {
    namespace = "net.kramb.lumen"
    compileSdk = 36

    defaultConfig {
        applicationId = "uc.plus.com"
        minSdk = 26
        targetSdk = 34
        versionCode = baseVersionCode
        versionName = lumenVersionName
        // Keep release artifacts product-named instead of AGP's generic
        // app-<variant>.apk names.
        setProperty("archivesBaseName", "uc-plus-$lumenVersionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // ABI selection is handled by the splits below; abiFilters cannot be combined with them.
    }

    splits {
        // One APK per ABI instead of a fat APK that carries all three native stacks. Each split
        // is a full app with the same signature, so they still replace one another in place.
        abi {
            isEnable = true
            reset()
            include(*supportedAbis.toTypedArray())
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "UC Plus Debug")
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**/OSGI-INF/MANIFEST.MF"
            // Build-time metadata that nothing reads at runtime.
            excludes += "/META-INF/*.version"
            excludes += "/DebugProbesKt.bin"
            pickFirsts += "/META-INF/LICENSE.md"
        }
        jniLibs {
            useLegacyPackaging = true
            // Tor and the transports ship their own copy of libc++; one is enough.
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}

// Portable JDK pin: works off JAVA_HOME or any detected/provisioned JDK 17, so no build
// script has to name a machine-specific JDK path.
kotlin {
    jvmToolchain(17)
}

// Signing needs the keystore on disk, so create it before any task that reads the signing config.
tasks.matching { it.name.startsWith("validateSigning") || it.name.startsWith("package") }.configureEach {
    dependsOn(ensureReleaseKeystore)
}

// Tor's GeoIP files (geoip, geoip6) map relay addresses to countries. They are needed to pick
// an exit country. They come from the Tor source at build time and are bundled as assets.
val downloadGeoip by tasks.registering {
    doLast {
        val dir = file("src/main/assets/geoip").apply { mkdirs() }
        val repoPaths = listOf(
            "https://raw.githubusercontent.com/torproject/tor/main/src/config/%s",
            "https://github.com/torproject/tor/raw/main/src/config/%s",
            "https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/%s",
            "https://gitlab.torproject.org/tpo/core/tor/-/raw/release-0.4.8/src/config/%s",
            "https://raw.githubusercontent.com/torproject/tor/release-0.4.8/src/config/%s",
            "https://raw.githubusercontent.com/torproject/tor/release-0.4.7/src/config/%s",
            "https://cdn.jsdelivr.net/gh/torproject/tor@main/src/config/%s",
            "https://raw.githubusercontent.com/torproject/tor/release-0.4.5/src/config/%s",
        )
        // Each line is "start,end,country" (addresses are numbers or text); check the shape only.
        val line = Regex("^[^,#\\s]+,[^,\\s]+,[A-Za-z?]{2}$")
        fun check(f: File, v6: Boolean): String? {
            if (!f.exists()) return "file was not created"
            val min = if (v6) 50_000L else 200_000L
            if (f.length() < min) return "size is only ${f.length()} bytes"
            val ok = f.useLines { ls -> ls.take(5000).any { line.matches(it.trim()) } }
            if (!ok) return "unexpected format"
            return null
        }
        val problems = mutableListOf<String>()
        var missing = false
        for (name in listOf("geoip", "geoip6")) {
            val v6 = name == "geoip6"
            val dest = File(dir, name)
            if (check(dest, v6) == null) {
                println("[UC-GEOIP] $name already present (${dest.length()} bytes)")
                continue
            }
            var done = false
            loop@ for (round in 1..2) for (pattern in repoPaths) {
                val u = pattern.format(name)
                val tmp = File(dir, "$name.part")
                try {
                    val c = java.net.URI(u).toURL().openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 20_000
                    c.readTimeout = 90_000
                    c.instanceFollowRedirects = true
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 uc-plus-build")
                    if (c.responseCode !in 200..299) {
                        println("[UC-GEOIP] $u -> HTTP ${c.responseCode}")
                        if (round == 2) problems += "$name: HTTP ${c.responseCode}"
                        continue
                    }
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    val why = check(tmp, v6)
                    if (why == null) {
                        dest.delete()
                        tmp.renameTo(dest)
                        println("[UC-GEOIP] $name downloaded (${dest.length()} bytes) from $u")
                        done = true
                        break@loop
                    }
                    println("[UC-GEOIP] $u rejected: $why")
                    tmp.delete()
                    if (round == 2) problems += "$name: $why"
                } catch (e: Exception) {
                    println("[UC-GEOIP] $u failed: ${e.javaClass.simpleName}: ${e.message}")
                    if (round == 2) problems += "$name: ${e.javaClass.simpleName}"
                }
            }
            if (!done && !v6) missing = true
        }
        if (missing) {
            println("::warning::[UC-GEOIP] geoip could not be downloaded; choosing an exit country will not work. " +
                problems.take(6).joinToString(" ; "))
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadGeoip) }

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier
            output.versionCode.set(baseVersionCode * 10 + (abiVersionCodeOffsets[abi] ?: 0))
        }
    }
}

composeCompiler {
    if (providers.gradleProperty("lumen.composeReports").orNull == "true") {
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
}

dependencies {
    // Project modules
    implementation(project(":ui"))
    implementation(project(":core:config"))
    implementation(project(":core:database"))
    implementation(project(":core:engine"))
    implementation(project(":core:vpn"))

    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Native camera UI with a bundled offline QR detector.
    val cameraXVersion = "1.5.3"
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.camera:camera-mlkit-vision:$cameraXVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
