# UC Plus (Android)

Android client for UC Plus, built with Kotlin and Jetpack Compose.

## Build

GitHub Actions builds everything: `.github/workflows/build-android.yml`.

- The three native engines (`libsingbox.so`, `libxray.so`, `libhev-socks5-tunnel.so`) are built from source on the runner by `android/tools/build_native_libs.sh` and cached.
- The release APKs are signed with `android/keystore/uc-plus-release.jks`, which is committed, so every build installs over the previous one.
- If `UC-Plus-source.zip` is uploaded to the repository root, the workflow unpacks it and builds.

Local build (needs JDK 17, Android SDK, NDK r28 and Go 1.27.3 for the native engines):

```bash
cd android
bash tools/build_native_libs.sh     # needs ANDROID_NDK_HOME
./gradlew assembleRelease
```

## Native engines

`android/core/engine/XRAY_NATIVE.md` and `android/core/vpn/HEV_NATIVE.md` describe the pinned versions. `android/tools/fetch_xray.py` downloads the verified official Xray releases for arm64 and x86_64.

## License

See `LICENSE` and `NOTICE.md`.
