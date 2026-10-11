# Build `synax.apk` on GitHub

This package contains the Android client, the separate V42 backend source, design references, and a GitHub Actions workflow. The workflow installs the required Android SDK packages, validates the repository layout, builds the debug APK, and uploads it as an artifact named `synax-apk`. If Gradle fails, it preserves `gradle-build.log` as a second artifact to help identify the exact error.

## Upload the package correctly

1. Download and extract this ZIP.
2. Create an empty GitHub repository.
3. Open a terminal in the extracted `apk-build-package` folder — the folder that directly contains `Android-App`, `Backend-V42`, `Design-References`, and `.github`.
4. Run these commands, replacing the URL with your repository URL:

   ```powershell
   git init
   git add -A
   git commit -m "Add SYNAX Android build source"
   git branch -M main
   git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
   git push -u origin main
   ```

   **Important:** Do not upload the outer folder as an extra nested directory. The `.github` folder and `Android-App` folder must be at the repository root. In the GitHub file list you should see both `.github/workflows/build-apk.yml` and `Android-App/settings.gradle.kts`.

5. On GitHub, open **Actions** and select **Build SYNAX APK**. If it doesn't run automatically after the push, select **Run workflow**.
6. When it succeeds, open the workflow run, scroll to **Artifacts**, download `synax-apk`, and extract it. The APK inside is named `synax.apk`.
7. If it fails, download the `synax-gradle-build-log` artifact (when available), or open the failed step **Build debug APK with detailed log** and copy the final 30–60 lines. The error message in those lines is needed to fix Kotlin/source/dependency errors specifically.

## What each folder is for

- `Android-App/`: Kotlin/Jetpack Compose app compiled into the APK.
- `Backend-V42/`: companion website/backend source. It is **not** bundled into the APK and must be deployed separately.
- `Design-References/`: provided screenshot references.

## Status and limits

The APK has not been successfully compiled or tested in the local sandbox. A successful GitHub Actions run is required before installing `synax.apk` on a phone. The native app still needs testing against the deployed SYNAX service on an Android device; native WebRTC call media is not implemented in this source.
