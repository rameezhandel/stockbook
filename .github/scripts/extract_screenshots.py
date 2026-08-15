#!/usr/bin/env python3
"""Pulls the screenshot attachments out of an .xcresult bundle.

    python3 extract_screenshots.py <bundle.xcresult> <output-dir>

`xcresulttool export attachments` writes files under generated names plus a
manifest.json that maps them back to the names the test gave them. This renames
them accordingly, so the output is `01-setup-name.png` rather than a UUID.

The manifest's exact shape has moved between Xcode versions, so rather than
assume one, this walks the whole structure looking for any object carrying both
an exported filename and a human-readable name. If the rename fails the raw
export is still left in place — worse names, same pictures.
"""

import json
import pathlib
import re
import shutil
import subprocess
import sys


def safe(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "-", name).strip("-") or "screenshot"


def find_pairs(node, found):
    """Depth-first walk for {exportedFileName, suggestedHumanReadableName}."""
    if isinstance(node, dict):
        exported = node.get("exportedFileName")
        readable = node.get("suggestedHumanReadableName") or node.get("name")
        if exported and readable:
            found.append((exported, readable))
        for value in node.values():
            find_pairs(value, found)
    elif isinstance(node, list):
        for value in node:
            find_pairs(value, found)
    return found


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    bundle = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2])
    if not bundle.exists():
        print(f"no result bundle at {bundle} — the test run probably never started")
        return 1

    raw = out / "_raw"
    raw.mkdir(parents=True, exist_ok=True)

    result = subprocess.run(
        ["xcrun", "xcresulttool", "export", "attachments",
         "--path", str(bundle), "--output-path", str(raw)],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print("xcresulttool export failed:")
        print(result.stderr.strip()[:2000])
        return 1

    manifest_path = raw / "manifest.json"
    renamed = 0
    if manifest_path.exists():
        pairs = find_pairs(json.loads(manifest_path.read_text()), [])
        for exported, readable in pairs:
            source = raw / exported
            if not source.exists() or source.suffix.lower() not in {".png", ".jpg", ".jpeg"}:
                continue
            target = out / f"{safe(readable)}{source.suffix.lower()}"
            shutil.copyfile(source, target)
            renamed += 1

    if renamed == 0:
        # No manifest, or nothing matched — keep every image we did get.
        for image in raw.rglob("*.png"):
            shutil.copyfile(image, out / image.name)
            renamed += 1

    shutil.rmtree(raw, ignore_errors=True)
    images = sorted(p.name for p in out.glob("*.png"))
    print(f"extracted {len(images)} screenshot(s):")
    for name in images:
        print(f"  {name}")
    return 0 if images else 1


if __name__ == "__main__":
    sys.exit(main())
