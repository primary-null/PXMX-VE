#!/usr/bin/env python3
"""Verify an APK's signature, pinned signer, and production manifest flags."""

import argparse
import pathlib
import re
import subprocess


def verify(apk, apksigner, aapt2, certificate_file):
    expected = pathlib.Path(certificate_file).read_text(encoding="utf-8").strip().lower()
    if not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise ValueError("Invalid pinned release certificate fingerprint")
    signed = subprocess.run(
        [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", signed)
    if [value.lower() for value in fingerprints] != [expected]:
        raise ValueError("APK signer does not match the pinned production certificate")
    manifest = subprocess.run(
        [str(aapt2), "dump", "badging", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout
    if "application-debuggable" in manifest or "application-testOnly" in manifest:
        raise ValueError("Debuggable or test-only APK cannot be released")
    xml = subprocess.run(
        [str(aapt2), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"],
        check=True, capture_output=True, text=True,
    ).stdout
    for line in xml.splitlines():
        if re.search(r"android:(debuggable|testOnly)\(", line):
            value = re.search(r"=\(type 0x12\)0x([a-fA-F0-9]+)", line)
            if value is None or int(value.group(1), 16) != 0:
                raise ValueError("Manifest enables a debug or test-only flag")
    if not re.search(r"^package: name='com\.pxmx\.app' ", manifest, re.MULTILINE):
        raise ValueError("Unexpected release application ID")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("--apksigner", required=True, type=pathlib.Path)
    parser.add_argument("--aapt2", required=True, type=pathlib.Path)
    parser.add_argument("--certificate", default="config/release-signing.sha256")
    args = parser.parse_args()
    verify(args.apk, args.apksigner, args.aapt2, args.certificate)
    print("Verified production signature, application ID, and manifest flags.")
