#!/usr/bin/env python3
"""Check an actual release APK. This does not certify Play Protect or store approval."""
import argparse
import re
import subprocess
from pathlib import Path


def validate_metadata(badging, manifest, certificate, package, preview=False):
    if not re.search(r"^package: name='" + re.escape(package) + r"'", badging, re.M):
        raise ValueError("Unexpected application ID")
    labels = re.findall(r"^application-label(?:-[^:]+)?:'([^']*)'", badging, re.M)
    if not labels or any(label != "امروز" for label in labels):
        raise ValueError("Every launcher label must be exactly امروز")
    if not re.search(r"^launchable-activity: name='[^']+'", badging, re.M):
        raise ValueError("No launcher activity")
    for attribute in ("debuggable", "testOnly"):
        if re.search(r"android:" + attribute + r"(?:\([^)]*\))?=.*(?:0xffffffff|true)", manifest):
            raise ValueError(f"Publication APK must not be {attribute}")
    if not preview and re.search(r"CN\s*=\s*Android Debug\b", certificate, re.I):
        raise ValueError("A debug certificate cannot be used for publication")
    if not re.search(r"certificate SHA-256 digest: [0-9a-f]{64}", certificate, re.I):
        raise ValueError("Missing verified signing certificate")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--preview", action="store_true", help="CI only: allow the preview ID and debug certificate")
    args = parser.parse_args()

    def run(tool, *arguments):
        return subprocess.run([str(args.build_tools / tool), *arguments], check=True, capture_output=True, text=True).stdout

    apk = str(args.apk.resolve())
    certificate = run("apksigner", "verify", "--verbose", "--print-certs", apk)
    run("zipalign", "-P", "16", "-c", "4", apk)
    badging = run("aapt", "dump", "badging", apk)
    manifest = run("aapt", "dump", "xmltree", apk, "AndroidManifest.xml")
    package = "com.nameemrooz.journal.preview" if args.preview else "com.nameemrooz.journal"
    validate_metadata(badging, manifest, certificate, package, args.preview)
    print("APK signature, alignment, launcher name, application ID and release flags: PASS")
    if args.preview:
        print("CI preview checked; this is not a publication certificate check.")


if __name__ == "__main__":
    main()
