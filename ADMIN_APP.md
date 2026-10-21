# DarkStore is now TWO apps

| | **DarkStore** (user app) | **DarkStore Admin** |
|---|---|---|
| Gradle flavor | `user` | `admin` |
| Package | `com.darkstore.darkroot` (unchanged - existing installs update normally) | `com.darkstore.darkroot.admin` |
| Who | everyone | owner / admins only |
| Screens | Games, Apps, My Library, Chat, Profile (+ Settings) | Console (all admin tools), Chat, Account |
| Contains admin code | **No** (stub only) | Yes |
| Sign-up / guest | yes | **no** - sign-in only, admin accounts only |
| Ads, widget, share & invite links | yes | no |
| Forced "update required" check | yes | no |

## Build
```bash
./gradlew assembleUserRelease      # DarkStore
./gradlew assembleAdminRelease     # DarkStore Admin
./gradlew assembleUserDebug assembleAdminDebug
```
Outputs: `app/build/outputs/apk/<user|admin>/<debug|release>/`. GitHub Actions uploads four artifacts
(`darkstore-USER-…`, `darkstore-ADMIN-…`, debug + RELEASE each). Install the RELEASE ones.

## One-time Firebase setup for the Admin app (required for Google sign-in + push)
1. Firebase console → Project settings → **Add app → Android** → package `com.darkstore.darkroot.admin`.
2. Add the same signing **SHA-1 and SHA-256** you use for the user app (CI run summary prints them).
3. Download `google-services.json` and **replace** `app/src/admin/google-services.json`
   (a temporary copy that reuses the user app's ids is included so the project builds).
4. Email + password sign-in already works without this step; Google sign-in and push notifications in the Admin app need it.

## Where the code lives
```
app/src/main/    shared: data layer, ViewModel, theme, user UI, backend client
app/src/user/    user-only: manifest parts (share/invite links, widget) + AdminConsoleStub.kt
app/src/admin/   admin-only: AdminConsole.kt, view/AdminNotifyPanel.kt, AdminApkStorageCard.kt, ServerStatusPanel.kt,
                 app name + icon resources, google-services.json
```
`com.example.utils.AppMode.isAdminApp` (a compile-time constant from `BuildConfig.IS_ADMIN_APP`) switches the few
behaviour differences in shared code.

## Security notes
* This is a UI/packaging separation. Admin power is still enforced **server-side** (RTDB rules check the `admin` role;
  the backend verifies an admin ID token). The Admin app signs out any non-admin account on sign-in.
* Keep the Admin APK private (share it only with admins). It is signed with the same key as the user app.

## Upgrading an existing checkout
The admin-only files moved from `app/src/main/.../view/` to `app/src/admin/.../view/`
(`AdminApkStorageCard.kt`, `AdminNotifyPanel.kt`, `ServerStatusPanel.kt`). Unzipping over an old checkout leaves the old
copies behind, which causes "Conflicting overloads". The build now deletes them automatically (task
`removeStaleMovedFiles`), but you can also delete them by hand.
