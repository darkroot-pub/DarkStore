# Dark Store - Security Files

This folder contains sensitive security-related files for the Dark Store Android application.

## Files

| File | Description |
|------|-------------|
| `darkstore-release.keystore` | Release signing keystore (keep secure!) |
| `google-services.json` | Firebase/Google Services configuration |
| `darkstore-keystore-details.txt` | Complete keystore details, passwords, fingerprints, and base64 encoded keystore |

## ⚠️ Security Warning

**These files contain sensitive credentials. Never commit them to version control.**

- The keystore contains the private key used to sign release builds
- The google-services.json contains Firebase project configuration
- The keystore-details.txt contains passwords and base64 encoded keystore

## Keystore Details

| Property | Value |
|----------|-------|
| **Keystore File** | `darkstore-release.keystore` |
| **Format** | PKCS#12 |
| **Alias** | `darkstore` |
| **Keystore Password** | `darkstore123` |
| **Key Password** | `darkstore123` |
| **Key Algorithm** | RSA 2048-bit |

## Certificate Fingerprints

Run this command to get fingerprints:
```bash
keytool -list -v -keystore darkstore-release.keystore -alias darkstore -storepass darkstore123 -keypass darkstore123
```

## Base64 Encoded Keystore (for CI/CD)

The full base64 encoded keystore is in `darkstore-keystore-details.txt`. Use for GitHub Actions secrets or CI/CD pipelines.

## Generating Keystore from Base64

```bash
# Decode base64 to keystore file (from darkstore-keystore-details.txt)
echo "<BASE64_STRING>" | base64 -d > darkstore-release.keystore
```

## Gradle Signing Config

Add to your app's `build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("darkstore-release.keystore")
            storePassword = System.getenv("DS_STORE_PASSWORD") ?: "darkstore123"
            keyAlias = "darkstore"
            keyPassword = System.getenv("DS_KEY_PASSWORD") ?: "darkstore123"
        }
    }
    buildTypes {
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
    namespace = "com.darkstore"
}
```

## Adding SHA to Firebase/Google Cloud

1. Get SHA-1 and SHA-256 from keystore (run keytool command above)
2. Add to Firebase Console → Project Settings → SHA fingerprints
3. Add to Google Cloud Console → APIs & Services → Credentials → OAuth 2.0 Client IDs

## ⚠️ Critical Notice

**Google Play Store permanently blocks app updates if this keystore or password is lost.**

- Store keystore file in Google Drive (encrypted)
- Store passwords in a password manager (Bitwarden, 1Password, etc.)
- Never commit keystore or passwords to git
- Use environment variables in CI/CD (GitHub Actions secrets)

## Repository

Source: https://github.com/DarkRoot-Organizations/Dark-Store
Built by: DarkRoot Organizations