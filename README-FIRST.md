# SYNAX delivery bundle

This bundle contains two independent parts:

- `Android-App/`: newly authored native Kotlin/Jetpack Compose project source.
- `Backend-V42/`: V41 website/backend source with reviewed-source patches for admin individual-message deletion and enforced emergency lockdown.

## Critical build status

No APK is included because the authoring environment does not have Android SDK Platform 35, `android.jar`, aapt/d8, or a Gradle installation/wrapper JAR; network access to download Android build dependencies is also unavailable. The Android project is source-only until it is opened and built in Android Studio on a machine with the required SDK/dependencies.

See `Android-App/README.md` for build instructions and known limitations. In particular, native WebRTC call media and in-app audio recording are not complete in this source. Do not treat this as a verified production release.

The backend patch is **not deployed**. Review it and test on staging before deploying to `synax.online`. No production data was changed by creating these files.
