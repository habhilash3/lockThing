# FocusLock (POCO M2 Reloaded · Android 11 · MIUI 12.5.7 · no root)

Use a selected app for 5 continuous minutes → it is blocked for 2 hours (from the ORIGINAL start; reopening never resets it) → access returns automatically.

## HONEST STATUS
- **No APK is included.** The environment that wrote this had no Android SDK / Gradle / Kotlin compiler and no access to Google's Maven repo, so the code has **never been compiled, installed or run**. Expect to fix a few compile errors on first build.
- **No on-device tests were performed.** The 12 unit tests in `RestrictionTest.kt` were written but also not run. Section "Test plan" lists what you must verify on the phone; every result is currently "NOT RUN".

## Build
Requirements: Android Studio Hedgehog+ (or JDK 17 + Android SDK 34 + Gradle 8.2).
1. Android Studio → Open → this folder → let it sync (the app has zero runtime dependencies; JUnit is test-only).
2. Debug APK: `gradle :app:assembleDebug` (or Build > Build APK) → `app/build/outputs/apk/debug/app-debug.apk`.
3. Unit tests: `gradle :app:testDebugUnitTest`.
4. Release: `keytool -genkey -v -keystore focuslock.jks -alias focus -keyalg RSA -keysize 2048 -validity 10000`, then Build > Generate Signed Bundle/APK > APK, or `gradle :app:assembleRelease` + `zipalign` + `apksigner sign --ks focuslock.jks ...`.
5. Install: `adb install app-debug.apk` (or copy to phone; allow "install unknown apps"). MIUI may also require turning on "Install via USB" in developer options.
(No Gradle wrapper jar is included; Android Studio provides Gradle, or run `gradle wrapper` once.)

## Mechanism evaluation (Android 11 / MIUI 12.5)
| Mechanism | Verdict |
|---|---|
| **AccessibilityService** (window-state events) | **Primary.** Event-driven, instant foreground detection, no polling. Can start activities from background and press HOME. Needs manual enabling; MIUI can kill it. |
| **UsageStatsManager** | **Supplement only.** Events lag and cannot be polled cheaply; used to seed state after unlock/service restart, cross-check the 5-min trigger, and prove a session was continuous after a restart. |
| Overlay (SYSTEM_ALERT_WINDOW) | Not used as the blocker (can be dismissed, leaves app usable). Requested only because it exempts the service from Android's background-activity-start limits. |
| Device admin / hidden APIs / root | Not used; cannot block individual apps. |
| MIUI "pop-up windows in background" permission | **Real risk:** MIUI denies background activity starts without it. Setup screen links to it. If denied, the service falls back to pressing HOME 1.2 s after detection, so enforcement still works but without the break screen. |

## How it works
- `FocusAccessibilityService` listens only to `TYPE_WINDOW_STATE_CHANGED` (no content access).
- **Session rules**: starts when a selected, unrestricted app is foreground; one Handler timer for the limit. Ends/resets when any other app, the launcher or FocusLock UI appears, or the screen turns off. Notification shade, keyboards and the `android` package are ignored (they don't count as leaving). No grace period: leaving for a second resets the counter.
- **On unlock / screen on / service restart**: foreground app is re-derived from UsageStats (keyguard dismissal emits no window event for the app underneath). A persisted session resumes only if usage events show no other app and no screen-off since it began.
- **At the limit**: timer fires → checks screen on, elapsed time, cross-checks UsageStats → writes the restriction (`commit()`) → launches `BlockingActivity`. If the activity doesn't reach the foreground, HOME is pressed.
- **Every foreground event** of an app with an active restriction re-launches the block screen. The restriction check runs before the "is selected" check, and a restricted app can't be deselected until expiry.
- `RestrictionManager.startIfNone` never changes an active restriction. Changing settings doesn't alter running restrictions.
- **Time handling**: within a boot, remaining time uses `elapsedRealtime` (immune to clock/timezone changes, counts deep sleep). After reboot it falls back to the saved wall-clock expiry (capped at the original duration) and re-bases. No BOOT receiver is needed: the system restarts enabled accessibility services itself, and state is evaluated lazily.
- **Block screen**: shows app name, message, real remaining time (read from storage each second), no bypass button; Back/Home/Recents exit to home and the task removes itself.
- Warning: if the service stops (user disabled it), a high-priority notification "protection is OFF" is posted from `onUnbind`; the dashboard shows red warnings; diagnostics page lists status.

## Permissions (all justified)
`PACKAGE_USAGE_STATS` (user-granted; validation/recovery), `SYSTEM_ALERT_WINDOW` (background-launch exemption), Accessibility (manual). No INTERNET, no wake locks, no foreground service (the accessibility service is already kept alive by the system), no boot receiver, no analytics. Data is in private SharedPreferences.

## MIUI guide (also in-app: "Setup & diagnostics")
1. Settings > Accessibility > Downloaded apps > FocusLock > On.
2. Settings > Privacy > Special permissions > Usage access > FocusLock > On.
3. Settings > Apps > Manage apps > FocusLock > Other permissions: **Display pop-up windows while running in the background** = On; Display over other apps = On.
4. Same page > Autostart = On.
5. Same page > Battery saver > **No restrictions**.
6. Recents: lock the FocusLock card (long-press / pull down > lock icon).
7. Notifications > FocusLock > allow.
Items 3–6 cannot be read back by any app, so FocusLock shows them as "cannot be verified". None of this guarantees uninterrupted operation.

## Known limitations
- Enforcement stops if you disable the accessibility service, Force stop the app, or uninstall it. Android gives ordinary apps no way to stop that. Restrictions persist and resume afterwards.
- If MIUI kills the service, it won't be running until you toggle it or MIUI restarts it. Time spent in a protected app while the service is dead is not blocked.
- Blocking is UI-level: audio/video already playing in the app (e.g. YouTube background play) is not stopped.
- Before the first reboot, the clock can't shorten a restriction; **across a reboot** the saved wall-clock time is used, so changing the clock before rebooting can alter it.
- Split-screen/floating windows and picture-in-picture may not emit a window event for the protected app.
- Events missed during the first ~second after process start are recovered via UsageStats only after unlock/screen-on or service connection.
- Deselecting/uninstalling: a restricted app stays blocked until expiry even if reinstalled (tracked by package name).
- Gesture/Recents transitions on MIUI's launcher are treated as "leaving" (resets the session).

## Test plan (all results: NOT RUN — must be done on the POCO M2 Reloaded)
Tip: set the limit to 1 minute and duration to 3 minutes in Settings for quick runs, then repeat once with 5 min / 2 h.
1. **Threshold**: select an app, use it continuously → blocked within ~1 s of the limit. Dashboard shows BLOCKED + expiry.
2. **Early exit**: use 2 min, switch away, return → counter restarts (needs the full limit again).
3. **Repeated reopening**: after block, Home, reopen 5+ times, also from Recents and notifications → block screen every time; note the expiry time on dashboard doesn't move.
4. **Other apps**: open an unselected app → unaffected.
5. **Expiration**: after the original expiry, app opens normally; dashboard returns to "Monitoring".
6. **Reboot**: trigger restriction, reboot → still blocked (after service starts); remaining time ≈ original.
7. **MIUI background**: enable all MIUI settings, swipe FocusLock from Recents (locked), idle 30+ min, test blocking; repeat with battery saver on "Restrictions" and record differences.
8. **Permission revocation**: turn off accessibility → red banner on dashboard + "protection OFF" notification; turn off usage access → warning shown.
9. **Screen-off**: use 4 min, lock/unlock phone, continue → counter should have reset.
10. **Unlock into restricted app**: lock phone with restricted app foreground, unlock → blocked.
Unit tests (simulated, host-JVM): `RestrictionTest` — expiry maths, clock changes, reboot re-basing, parsing.
