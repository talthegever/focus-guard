# Focus Guard

Personal Android app that makes YouTube and Instagram less of a time sink. An accessibility service watches those two apps and leaves a blocked screen as soon as it opens. It does not modify the apps and needs no root, VPN, or certificate.

The folder is still named `focus-proxy` for historical reasons (see "History").

## What is blocked

| Rule key | Screen | Detection | Reaction |
|---|---|---|---|
| `youtube_shorts` | YouTube Shorts player, and the Shorts shelf on the home feed | view id `reel_progress_bar` or `reel_recycler` visible | system Back |
| `instagram_reels_tab` | Instagram Reels tab | view id `clips_tab` selected | tap `feed_tab`, else Back |

Instagram Explore is deliberately not blocked for now (owner's decision). Reels opened from a DM, the feed, or Explore are deliberately allowed.

**Status: `youtube_shorts` fires on the owner's phone; everything else is unverified.** On 2026-11-16 (YouTube 21.45.82, POCO `duchamp`) the service left Shorts twice on signal `reel_recycler`. `reel_progress_bar` is checked first and did not match either time, so it is either gone or appears later than the recycler. The Shorts shelf on the home feed triggers the rule too (owner's report, one `reel_recycler` block in the log); the owner wants it that way, so do not narrow the rule to the player. Not yet checked: that normal videos and a home feed without the shelf are left alone. `instagram_reels_tab` is still an untested candidate from other open-source blockers; Instagram is not installed on the phone. The fixtures in `app/src/test/resources/screens/` are still hand-written placeholders (marked `source="synthetic"`). The next task is the discovery procedure below; to dump the Shorts screen, switch the rule off in the app first or the service leaves before the dump.

## Layout

- `app/src/main/kotlin/com/focusguard/app/`
  - `Rules.kt`: the whole policy. The file to edit when detection breaks.
  - `Matcher.kt`: pure Kotlin, no Android imports. `Screen` interface plus rule evaluation.
  - `BlockerService.kt`: the `AccessibilityService`. Looks views up with `findAccessibilityNodeInfosByViewId`, applies a 1.5 s cooldown per rule, logs with tag `FocusGuard`.
  - `MainActivity.kt`: setup checklist (each step opens the exact settings page, with fallbacks) and one switch per rule (SharedPreferences `rules`).
- `app/src/debug/.../DebugHooks.kt`: debug builds only. On broadcast `com.focusguard.app.DUMP` it writes the active window's view tree to `files/dump.xml`. `app/src/release/` has a no-op twin.
- `app/src/main/res/xml/blocker_service.xml`: service config, including the watched `packageNames`.
- `app/src/main/res/drawable/ic_launcher_*.xml`: the launcher icon (shield with a struck-through play button), vector only. The monochrome file is a hand-kept copy of the foreground's shapes; change both together.
- `app/src/test/`: `MatcherTest` runs `Rules.all` against the screen dumps; `DumpScreen` parses them.
- `tools/dump_screen.py`: pulls a dump over adb into the fixtures folder. `tools/test_dump_screen.py` tests it.
- `scripts/install.ps1`: build, `adb install`, then one-time setup over adb: appends the service to `enabled_accessibility_services` (keeping other services), adds the app to the Doze allowlist, and opens the setup screen.

## Commands

```powershell
.\gradlew.bat --console=plain -q :app:testDebugUnitTest   # matcher tests against the dumps
python -m unittest discover -s tools -v                    # dump tool tests
.\gradlew.bat --console=plain -q :app:assembleDebug        # app\build\outputs\apk\debug\app-debug.apk
.\scripts\install.ps1                                      # build + install + switch the service on over adb
.\scripts\release.ps1                                      # signed release APK -> dist\focus-guard-<version>.apk
python tools\dump_screen.py <name> [--all-ids]             # save the open screen as a fixture
adb logcat -s FocusGuard                                   # what the service saw and did
```

Gradle prints nothing on success with `-q`; check `app/build/test-results/` if you need the test count.

## Discovery: fixing or adding a rule

Do this when a rule stops firing after an app update, and once now to replace the placeholders.

1. Run `.\scripts\install.ps1` and check that every step on the app's setup screen is ticked.
2. Open the target screen on the phone and run `python tools\dump_screen.py <name>`. Use the existing fixture names (`youtube_shorts`, `youtube_home`, `youtube_video`, `instagram_feed`, `instagram_explore`, `instagram_reels_tab`, `instagram_dm`, `instagram_dm_reel`) so the tests pick the dump up.
3. Read the printed "selected ids" and "ids worth a look"; compare the blocked screen's dump against its neighbours to find an id that appears only on the blocked screen.
4. Update `Rules.kt`, run the unit tests, reinstall, and confirm with `adb logcat -s FocusGuard`.

A rule for a new app also needs its package in `blocker_service.xml` and in the manifest's `<queries>`.

## Things that are easy to get wrong

- Matching is by exact id entry name (the part after `:id/`), and the id's package must equal the app package. A view that is present but not visible does not count.
- `Signal(mustBeSelected = true)` is what separates "the Reels tab is open" from "the tab bar is on screen".
- Dumps contain ids, classes, state flags, and bounds only. Never add text or content descriptions to them; they would capture private on-screen content. `dump_screen.py` enforces this with an attribute allowlist.
- `rootInActiveWindow` is null on some system windows (seen on the permission prompt). The dump then logs `dump: no active window` and the tool times out.
- Use the SDK's adb (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). The `adb` on PATH (`C:\Windows\adb`) is an older version and restarts the adb server when mixed with it. The tool and script already prefer the SDK one.
- An app cannot grant itself accessibility access or battery exemptions. Setup is therefore split: `install.ps1` does what adb can, and the setup screen deep-links to the rest.
- On POCO/HyperOS the system kills the service unless the app has Autostart enabled and battery saver set to "No restrictions". Neither can be set over adb or read back, so the setup screen shows them as unchecked bullets. Its Xiaomi deep links (`com.miui.securitycenter` autostart, `com.miui.powerkeeper` per-app saver) are untested on a real device; each falls back to App info.
- `settings put secure` over adb fails on Xiaomi/POCO unless "USB debugging (Security settings)" is on in Developer options. The script then warns and the in-app "Turn on" button is the path.
- Sideloaded apps can show "Restricted setting" when enabling accessibility by hand: App info → ⋮ → Allow restricted settings. The setup screen has a button for it.
- The setup screen's button targets have not been seen working yet (only that the screen launches without crashing).

## Releases

The APK for other people is the release build, never the debug one: the debug build exports the dump receiver and is signed with this machine's debug key.

- `scripts\release.ps1` runs the unit tests, builds `:app:assembleRelease`, checks the signature with `apksigner`, and copies the APK and `INSTALL.md` to `dist\` (ignored by git; attach the APK to a release instead of committing it).
- `INSTALL.md` in the project root is the install guide for people who get the APK. Edit it there; the copy in `dist\` is overwritten on every release build.
- Signing reads `keystore.properties` in the project root (ignored): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. The key is `%USERPROFILE%\.android\focus-guard-release.jks`, alias `focusguard`. Neither file is backed up anywhere. If the key or its password is lost, nobody can install a later version over the one they have; they must uninstall first.
- Bump `versionCode` and `versionName` in `app/build.gradle.kts` before each release that goes out.
- The release and debug builds share an application id but not a signature, so one cannot be installed over the other: uninstall first. Dumps need the debug build, so switch back with `install.ps1` before discovery.
- On 2026-11-17 release 0.1.0 was sideloaded on the owner's phone following `INSTALL.md` (debug build uninstalled first) and blocked Shorts. The owner's phone now runs the release build.

## Build environment

- AGP 9.1.1 with its built-in Kotlin support (no Kotlin Gradle plugin applied), Gradle 9.3.1 wrapper, JDK 17.
- compileSdk 37, targetSdk 34, minSdk 26. No third-party runtime dependencies; JUnit 4 for tests. No INTERNET permission.
- `local.properties` (ignored) holds `sdk.dir`; `ANDROID_HOME` is not set on this machine.
- There is no emulator. The old AVD and its system image were deleted on the owner's request: it could not run current YouTube or Instagram and kept freezing. Test on the real phone.

## History

This repo used to be a PCAPdroid fork with an on-device mitmproxy addon that blocked API endpoints. It was dropped because YouTube and Instagram ignore user-installed CAs, so the proxy could never see a URL path on a stock phone; no endpoint was ever captured.

- Old tree: tag `archive/pcapdroid-proxy`.
- The addon's two local-only commits: tag `archive/focus-proxy-addon` inside `.git/modules/mitm-addon` (read with `GIT_DIR=.git/modules/mitm-addon GIT_WORK_TREE=. git log archive/focus-proxy-addon`).
- On 2026-11-17 `main` was rebuilt to start at the PCAPdroid removal, because the clone is shallow (GitHub rejects pushes of shallow history). The old commits live only in the local tag above and branch `backup/pre-rewrite`; neither can be pushed.
- Remote `origin` is the public repo github.com/talthegever/focus-guard. Release `v0.1.0` there carries the APK. The signing key and `keystore.properties` are still only on this machine. `upstream` (PCAPdroid) is a leftover remote and is not used.
