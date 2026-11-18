# Voice Assistant (Android)

Kotlin Android app, package `com.example.voiceassistant`.

- minSdk 26, targetSdk 34, compileSdk 34
- JDK 17 + Gradle 8.7 + Android SDK 34
- Build command: `gradle assembleDebug`
- Gemini API key is entered by the user in the app and stored in local SharedPreferences; it is not hardcoded.
- Hindi (`hi-IN`) speech recognition, Gemini JSON action planning, app launch, contacts, call, WhatsApp, and Accessibility actions.

## Build APK on GitHub using only a phone
1. Create a GitHub repository.
2. Upload the CONTENTS of this ZIP (the `app`, `.github`, `build.gradle`, `settings.gradle`, and other files) to the repository root. Do not upload an extra enclosing folder.
3. Open the repository's **Actions** tab and run **Build Android APK** (or push to `main`).
4. Open the completed workflow run, scroll to **Artifacts**, and download `app-debug`.

## On the phone
1. Install the APK. If Android blocks installation, allow your browser permission to install unknown apps.
2. Open the app and enter your own Gemini API key in the key field, then tap Save API Key.
3. Grant microphone, contacts, and phone permissions. Enable the app's Accessibility service from the button in the app.

Calling and WhatsApp sending ask for confirmation first. WhatsApp's final Send-button automation depends on the WhatsApp version, screen state, and Accessibility permission; check the chat before confirming.


## Hey Vyron wake word
1. App kholkar **Wake word ON: Hey Vyron** dabayein. Persistent notification dikhna chahiye.
2. Screen off mein “Hey Vyron” bolkar wake-up test karein. Wake word detect hone par app khulkar command sunne ki koshish karega.
3. Band karne ke liye **Wake word OFF** dabayein.

Note: This uses Android's standard SpeechRecognizer as a best-effort listener, not a dedicated low-power hotword DSP. It needs internet/recognition provider and microphone permission, may consume battery, and Android/OEM battery policies can interrupt it. Screen-off wake-up is not guaranteed on every phone.
