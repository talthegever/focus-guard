#!/usr/bin/env python3
"""Save the view tree of the screen currently open on the phone as a test fixture.

Open the screen you want to capture in YouTube or Instagram, then run:

    python tools/dump_screen.py youtube_shorts

The debug build of Focus Guard must be installed and switched on in Accessibility
settings. The dump holds view ids and state flags only, never on-screen text.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

APP_PACKAGE = "com.focusguard.app"
DUMP_ACTION = "com.focusguard.app.DUMP"
DEVICE_FILE = "files/dump.xml"
DEFAULT_OUT = Path(__file__).resolve().parents[1] / "app" / "src" / "test" / "resources" / "screens"

SCREEN_ATTRIBUTES = ("package", "appVersion", "source")
NODE_ATTRIBUTES = ("id", "class", "selected", "visible", "clickable", "bounds")
INTERESTING = re.compile(r"reel|short|clips|tab|pivot|explore|search", re.IGNORECASE)


def resolve_adb(requested: str) -> str | None:
    """Prefer the Android SDK's adb: an older adb on PATH keeps restarting the server."""
    if requested != "adb":
        return shutil.which(requested)
    sdk_roots = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")]
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        sdk_roots.append(str(Path(local_app_data) / "Android" / "Sdk"))
    name = "platform-tools/adb.exe" if os.name == "nt" else "platform-tools/adb"
    for sdk_root in sdk_roots:
        if sdk_root and (Path(sdk_root) / name).is_file():
            return str(Path(sdk_root) / name)
    return shutil.which(requested)


def sanitize(raw_xml: str) -> ET.Element:
    """Rebuild the dump keeping only allowlisted attributes, so no text can slip through."""
    source = ET.fromstring(raw_xml)
    if source.tag != "screen":
        raise ValueError("dump does not start with <screen>")

    def copy(node: ET.Element, allowed: tuple[str, ...]) -> ET.Element:
        clean = ET.Element(node.tag, {key: node.attrib[key] for key in allowed if key in node.attrib})
        for child in node:
            if child.tag == "node":
                clean.append(copy(child, NODE_ATTRIBUTES))
        return clean

    return copy(source, SCREEN_ATTRIBUTES)


def summarize(screen: ET.Element) -> list[str]:
    nodes = list(screen.iter("node"))
    ids = [node.get("id", "").split(":id/", 1)[-1] for node in nodes if node.get("id")]
    selected = sorted({
        node.get("id", "").split(":id/", 1)[-1]
        for node in nodes
        if node.get("selected") == "true" and node.get("id")
    })
    interesting = sorted({view_id for view_id in ids if INTERESTING.search(view_id)})
    return [
        f"app: {screen.get('package')} {screen.get('appVersion')}",
        f"nodes: {len(nodes)} ({len(ids)} with an id, {len(set(ids))} distinct ids)",
        "selected ids: " + (", ".join(selected) or "none"),
        "ids worth a look: " + (", ".join(interesting) or "none"),
    ]


def adb_command(adb: str, serial: str | None, *args: str) -> list[str]:
    return [adb, *(["-s", serial] if serial else []), *args]


def pull_dump(adb: str, serial: str | None, wait_seconds: float) -> str:
    def run(*args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            adb_command(adb, serial, *args), capture_output=True, text=True, encoding="utf-8", errors="replace"
        )

    devices = run("devices").stdout.splitlines()[1:]
    ready = [line.split("\t")[0] for line in devices if line.strip().endswith("\tdevice")]
    if not ready:
        raise RuntimeError("No authorized Android device. Connect the phone and approve USB debugging.")
    if not serial and len(ready) > 1:
        raise RuntimeError("Several devices are connected; pass --serial. Found: " + ", ".join(ready))

    run("shell", "run-as", APP_PACKAGE, "rm", "-f", DEVICE_FILE)
    sent = run("shell", "am", "broadcast", "-a", DUMP_ACTION, "-p", APP_PACKAGE)
    if sent.returncode != 0:
        raise RuntimeError("Could not send the dump request: " + (sent.stderr or sent.stdout).strip())

    deadline = time.monotonic() + wait_seconds
    while time.monotonic() < deadline:
        result = run("exec-out", "run-as", APP_PACKAGE, "cat", DEVICE_FILE)
        if result.returncode == 0 and result.stdout.lstrip().startswith("<?xml"):
            return result.stdout
        time.sleep(0.3)
    raise RuntimeError(
        "No dump arrived. Check that the debug build is installed and that Focus Guard is "
        "switched on in Accessibility settings, then keep the target screen open and retry. "
        "`adb logcat -s FocusGuard` shows what the service saw."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("name", help="fixture name, e.g. youtube_shorts or instagram_reels_tab")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="directory for the fixture")
    parser.add_argument("--adb", default="adb", help="adb executable or absolute path")
    parser.add_argument("--serial", help="device serial when several devices are connected")
    parser.add_argument("--wait", type=float, default=6.0, help="seconds to wait for the dump")
    parser.add_argument("--all-ids", action="store_true", help="also print every distinct view id")
    args = parser.parse_args()

    if re.fullmatch(r"[a-z0-9_]+", args.name) is None:
        parser.error("name must use lowercase letters, digits, and underscores only")
    adb = resolve_adb(args.adb)
    if adb is None:
        print(f"ERROR: adb not found: {args.adb}", file=sys.stderr)
        return 2

    try:
        screen = sanitize(pull_dump(adb, args.serial, args.wait))
    except (RuntimeError, ValueError, ET.ParseError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    ET.indent(screen)
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / f"{args.name}.xml"
    target.write_bytes(ET.tostring(screen, encoding="utf-8", xml_declaration=True) + b"\n")

    print(f"saved {target}")
    for line in summarize(screen):
        print(line)
    if args.all_ids:
        for view_id in sorted({n.get("id", "") for n in screen.iter("node") if n.get("id")}):
            print("  " + view_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
