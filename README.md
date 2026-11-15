# Focus Guard

A small Android app that leaves distracting screens as soon as they open:

- **YouTube Shorts**: the Shorts player closes (system Back).
- **Instagram Reels tab**: you are sent back to Home.

Everything else keeps working, including Instagram Explore and reels that someone sends you. The original apps are not modified. The app has no internet permission and reads only view ids, never on-screen text.

## Install

1. Enable Developer options and USB debugging on the phone and connect it.
2. From PowerShell in this folder:

   ```powershell
   .\scripts\install.ps1
   ```

3. On the phone: Settings → Accessibility → Downloaded apps → **Focus Guard** → on.
   - If Android says "Restricted setting": open Focus Guard's App info → ⋮ → **Allow restricted settings**, then try again.
4. On POCO/HyperOS, also enable **Autostart** for Focus Guard and set its battery saver to **No restrictions**. Otherwise the system stops the service.

Open the Focus Guard app to see whether the blocker is running and to switch individual rules off.

## Limits

- It reacts after the screen opens, so the blocked screen can flash for a moment.
- The Shorts shelf on the YouTube home feed stays visible; tapping a Short sends you back.
- You can turn the service off in Settings. This adds friction; it is not a lock.
- YouTube and Instagram updates can rename views, which silently disables a rule until it is re-tuned. See "Discovery" in [CLAUDE.md](CLAUDE.md).

## Development

See [CLAUDE.md](CLAUDE.md) for the layout, commands, and how to fix a rule after an app update.
