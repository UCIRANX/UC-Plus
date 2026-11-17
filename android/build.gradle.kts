plugins {
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}

// Tor and IPtProxy pull in a newer kotlin-stdlib (2.2.x) than this project's Kotlin 2.0.20 compiler
// can read. Keep every module on the stdlib that matches the compiler.
subprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")
    }
}
