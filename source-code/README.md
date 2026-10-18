# Dark Store - Source Code

This folder contains the complete Kotlin source code for the Dark Store Android application.

## Structure

```
source-code/
├── main/                    # Main source set
│   ├── AndroidManifest.xml  # App manifest
│   ├── kotlin/              # Kotlin source files (package: com.darkstore)
│   │   ├── data/            # Data layer (entities, DAOs, repositories)
│   │   ├── utils/           # Utility classes
│   │   └── view/            # UI components (Jetpack Compose)
│   └── res/                 # Resources (layouts, drawables, values, xml)
├── build.gradle.kts         # App-level Gradle build script (Kotlin DSL)
├── settings.gradle.kts      # Project settings
├── gradle.properties        # Gradle properties
└── build.gradle.kts         # Project-level Gradle build script (Kotlin DSL)
```

## Key Components

### Data Layer (`data/`)
- **Entities**: AppEntity, BannerEntity, ChatEntity, CollectionEntity, DownloadEntity, ReviewEntity, UserEntity, etc.
- **DAOs**: AppDao for database operations
- **Repositories**: AppRepository for data access
- **Services**: FirebaseAuthService, FirebaseService

### Utils (`utils/`)
- **ApkInstaller**: APK installation handling
- **CustomDownloadManager**: Download management
- **DownloadForegroundService**: Foreground download service
- **SecureStore**: Secure storage utilities
- **SigningInfo**: APK signature verification
- **StorageManager**: Storage operations
- **FraudRules**: Fraud detection rules
- **AdminPin**: Admin PIN management

### UI (`view/`)
- **Screens**: AuthScreen, MainActivity
- **Components**: BannersUi, CollectionsUi, AppDetailsParts, ChatTabContent
- **Admin**: AdminApkStorageCard, AdminNotifyPanel, ApkUploadSection
- **Dialogs**: ChangeAdminPinDialog, DeveloperProfileDialog, EcosystemPolicyDialog

## Building

Open the project in Android Studio or build via command line:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## Requirements

- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 34
- Kotlin 1.9+