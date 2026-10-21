package com.example.utils

import com.example.BuildConfig

/**
 * DarkStore ships as TWO apps built from one codebase (Gradle product flavors):
 *
 *  - `user`  -> DarkStore marketplace (package `com.darkstore.darkroot`). No admin console is compiled in.
 *  - `admin` -> DarkStore Admin (package `com.darkstore.darkroot.admin`). Only the admin tools; sign-up, ads,
 *               share/invite links, the widget and forced user-app updates are switched off.
 *
 * `isAdminApp` is a compile-time constant, so the other flavor's branches are removed by the compiler/R8.
 * This is a UI separation only: the REAL admin protection stays on the server (RTDB rules + backend admin-token checks).
 */
object AppMode {
    const val isAdminApp: Boolean = BuildConfig.IS_ADMIN_APP

    /** First tab after login: the console for admins, the store front for everyone else. */
    val homeTab: String = if (isAdminApp) "Console" else "Apps"
}
