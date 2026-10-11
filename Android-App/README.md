# SYNAX Native Android (fresh project)

This is a newly authored native Android client for the existing SYNAX service. It uses Kotlin, Jetpack Compose, Material 3, OkHttp, coroutines, and Android Keystore-backed token encryption. It is not a WebView wrapper.

## Open and build

Requirements:
- Android Studio with Android SDK Platform 35 installed
- JDK 17 (Android Studio's bundled JDK is suitable)
- Gradle 8.9 or compatible with Android Gradle Plugin 8.7.3

Open this folder (`SYNAX-Android`) in Android Studio and allow Gradle sync. This delivery includes the Gradle wrapper properties but does not include `gradlew` / `gradle-wrapper.jar` because Gradle isn't installed in the build environment. On a computer with Gradle 8.9 installed, run from this folder:

```bash
gradle wrapper --gradle-version 8.9
```

Then in Android Studio select **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The expected debug APK path is `app/build/outputs/apk/debug/app-debug.apk`.

The configured base URL is `https://synax.online` in `app/build.gradle.kts` as `SYNAX_BASE_URL`. This value is public configuration, not a secret. No Supabase service key or admin password is bundled.

## What is implemented in this source

- Fresh native Compose login experience for Person 1, Person 2 and separate admin login.
- Encrypted local session-token storage using Android Keystore-backed encryption.
- API client targeting the current SYNAX HTTP contracts.
- WebSocket connection/authentication/reconnect and heartbeat basics.
- Native chat list, text messages, image/video/document attachment sending, read marking, replies/reactions/edit/delete/pin actions as supported by V41.
- V41 resumable upload endpoints for photos/videos; sequential 1 MiB chunks and offset reconciliation. Smaller documents/audio use the existing raw upload endpoint within the app's 25 MiB client-side limit.
- Profile editor and shared chat-background choices/photo upload.
- Native microphone recording controls with voice-message upload/send flow (needs device verification).
- Native administrator sections for overview, users, logos, time rules, feature settings, atmosphere, message audit, emergency lockdown/password, audit trail, and database export/reset.
- Admin-specific single-message deletion UI with confirmation, along with a separate clear-all action.

## Important limits; read before production use

- This source has **not been compiled into an APK in this environment**. The environment has no Android SDK/`android.jar`, Gradle executable, wrapper JAR, or network access to obtain build dependencies. Kotlin/Compose compile and device runtime tests are therefore still required in Android Studio.
- Native WebRTC voice/video call media is **not implemented in this delivery**. The call UI currently opens the existing web call route rather than claiming native calls work. The WebRTC dependency is present but unused. A native call manager and device-level call tests remain required.
- Native voice recording/send has been added at source level but has not been compiled or tested on a physical Android device.
- Real-time state reconciliation and edge cases still need device/live-backend testing. Do not treat static source inspection as successful live testing.
- The admin JSON export gathers the data the existing authorized endpoints expose; confirm the export scope before using it as a backup.

## Backend changes supplied separately

The companion V42 backend ZIP adds:
- An admin-authenticated `DELETE /api/admin/messages/:id` route. It tombstones only one selected message, removes its active attachment URL, records an audit event, and broadcasts `chat:message_deleted`. It deliberately does not delete the underlying storage object, to avoid unsafe deletion of shared or unrelated objects.
- An `emergencyLockdown` state and lockdown message enforced in user authentication middleware/login, plus session invalidation and realtime socket closure on activation.

These backend changes are **not deployed by building the Android app**. Review the diff, test on a staging deployment, then deploy the patched backend separately. Existing production data is not changed by this archive.

## Security notes

- Never add Supabase service-role secrets, TURN API credentials, or admin passwords to the Android app.
- Rotate any administrator password that may have been exposed by screenshots or previous code.
- Confirm that Cloudflare and local backend versions stay consistent after deploying the patch.
