# Focus Guard

A small Android app that leaves distracting screens as soon as they open:

- **YouTube Shorts**: the Shorts player closes (system Back).
- **Instagram Reels tab**: you are sent back to Home.

Everything else keeps working, including Instagram Explore and reels that someone sends you. The original apps are not modified. The app has no internet permission and reads only view ids, never on-screen text.

## Install from the APK

No computer needed. The step-by-step guide is [INSTALL.md](INSTALL.md); `scripts\release.ps1` puts a copy next to the APK in `dist\` so the two can be sent together.

## Install from source

1. Enable Developer options and USB debugging on the phone and connect it.
2. From PowerShell in this folder:

   ```powershell
   .\scripts\install.ps1
   ```

   The script installs the app, switches its accessibility service on, lifts Android's battery limit, and opens the app's setup screen on the phone.

3. On the setup screen, finish any step that is not ticked. Each step has a button that opens the right settings page.
   - On Xiaomi/POCO, the script can only switch the service on if **USB debugging (Security settings)** is enabled in Developer options. If it is not, use the **Turn on** button.
   - On Xiaomi/POCO, **Autostart** and **Battery saver: No restrictions** always have to be set by hand, once. Without them the system stops the blocker.

Open the Focus Guard app any time to see the setup state and to switch individual rules off.

## Limits

- It reacts after the screen opens, so the blocked screen can flash for a moment.
- The Shorts shelf on the YouTube home feed counts as Shorts: when it scrolls into view, Focus Guard presses Back.
- You can turn the service off in Settings. This adds friction; it is not a lock.
- YouTube and Instagram updates can rename views, which silently disables a rule until it is re-tuned. See "Discovery" in [CLAUDE.md](CLAUDE.md).

## Development

`.\scripts\release.ps1` builds the signed APK into `dist\`. See [CLAUDE.md](CLAUDE.md) for the layout, commands, and how to fix a rule after an app update.
