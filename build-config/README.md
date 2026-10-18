# Dark Store - Build Configuration

This folder contains build configuration files including ProGuard rules and signing information.

## Files

| File | Description |
|------|-------------|
| `proguard-rules.pro` | ProGuard/R8 optimization and obfuscation rules |

## ProGuard Rules (`proguard-rules.pro`)

The ProGuard configuration includes rules for:

- **Firebase**: Keep Firebase classes and annotations
- **Kotlin**: Keep Kotlin metadata and coroutines
- **Room**: Keep database entities and DAOs
- **Retrofit/OkHttp**: Keep network layer classes
- **Jetpack Compose**: Keep Compose runtime classes
- **Custom**: App-specific keep rules for reflection/serialization

### Key Rules

```proguard
# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# App specific
-keep class com.darkstore.data.** { *; }
-keep class com.darkstore.utils.** { *; }
```

## App Signing (SHA Fingerprints)

### Release Keystore
- **File**: `../security-files/darkstore-release.keystore`
- **SHA-1**: Run to get fingerprint:
  ```bash
  keytool -list -v -keystore darkstore-release.keystore -alias <alias>
  ```
- **SHA-256**: Run to get fingerprint:
  ```bash
  keytool -list -v -keystore darkstore-release.keystore -alias <alias> -J-Duser.language=en
  ```

### Debug Keystore (Auto-generated)
- Location: `~/.android/debug.keystore`
- Get SHA-1:
  ```bash
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
  ```

## Adding SHA to Firebase/Google Cloud

1. Get SHA-1 and SHA-256 from release keystore
2. Add to Firebase Console → Project Settings → SHA fingerprints
3. Add to Google Cloud Console → APIs & Services → Credentials → OAuth 2.0 Client IDs

## Build Types

| Type | Signing | ProGuard | Use Case |
|------|---------|----------|----------|
| `debug` | Debug keystore | Disabled | Development |
| `release` | Release keystore | Enabled | Production |

## Gradle Configuration

ProGuard is enabled in `app/build.gradle.kts`:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }
}
```

## Verifying ProGuard Output

After release build, check:
- `app/build/outputs/mapping/release/mapping.txt` - Class mapping
- `app/build/outputs/apk/release/app-release.apk` - Optimized APK
- Use `apkanalyzer` or `bundletool` to verify