# Build `synax.apk`

This delivery includes a GitHub Actions workflow at `.github/workflows/build-apk.yml`. The current build sandbox has no Android SDK, Gradle executable, `android.jar`, or network access for downloading the missing Android build dependencies, so an APK could not be compiled locally in this session.

## Build on GitHub

1. Extract this ZIP. Open the extracted `apk-build-package` folder in PowerShell or a terminal.
2. Create an **empty** repository on GitHub (do not add a README or license there).
3. From the extracted folder, run the following commands. Replace the URL with your new repository URL:

   ```powershell
   git init
   git add -A
   git commit -m "Add SYNAX Android build source"
   git branch -M main
   git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
   git push -u origin main
   ```

4. On GitHub, open the repository's **Actions** tab and select **Build SYNAX APK**. The workflow should start automatically after the push. If it does not, click **Run workflow**.
5. After the workflow succeeds, open the completed run and download the `synax-apk` artifact. Extract the downloaded artifact ZIP to get `synax.apk`.

If the workflow fails, open the failed step and share its log. The APK should be treated as unverified until the build succeeds and the app is tested on an Android device.

## What is included

- `Android-App/` is the native Kotlin/Jetpack Compose client and the only part compiled into the APK.
- `Backend-V42/` is the separate website/backend source. It is included for completeness but cannot simply be packaged into the APK; backend patches require review, staging tests, and a separate deployment.
- `Design-References/` contains the supplied screenshots.

Read `Android-App/README.md` for app features and current limitations. Native WebRTC call media is not complete in this source, and live backend/device testing remains necessary.
