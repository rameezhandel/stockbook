#!/usr/bin/env python3
"""
The cross-platform invariants, checked in about a second.

Stockbook is two apps held to one spec, and the expensive failures are the ones
where they quietly stop agreeing. None of those are visible on a screen, and most
are only caught by a CI round trip — five to ten minutes on iOS, where nothing
can be compiled locally.

So the rules that have actually cost round trips are asserted here instead:

    python3 tools/check.py

Static only, and deliberately so. It parses text; it does not build anything.
Run it *with* `cd android && ./gradlew :core:test`, never instead of it.

Three checks, and the shortness is the point.

All three are comparisons of one flat set or count against another, with nothing
to misparse. Structural checks were tried here and removed: one walked Swift
decoders, one counted call sites, and both produced false positives within
minutes of being written. A check that cries wolf is worse than no check, because
the next person learns to skip the whole script — and what those two looked for is
already covered by real tests that read a real file.

Do not add a check on suspicion. Add one when something has actually gone wrong,
and only when it can be expressed without parsing structure.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

KOTLIN_STRINGS = ROOT / "android/core/src/main/kotlin/com/stockbook/core/text/Strings.kt"
SWIFT_STRINGS = ROOT / "ios/Stockbook/Support/Localization/Strings.swift"
SWIFT_LOCALIZATION_TESTS = ROOT / "ios/StockbookTests/LocalizationTests.swift"
KOTLIN_SELL_SCREEN = ROOT / "android/app/src/main/kotlin/com/stockbook/app/feature/sell/SellScreen.kt"
SWIFT_SELL_SCREEN = ROOT / "ios/Stockbook/Features/Sell/SellScreen.swift"

failures: list[str] = []


def fail(check: str, detail: str, why: str) -> None:
    failures.append(f"✗ {check}\n  {detail}\n  → {why}")


def ok(check: str, detail: str) -> None:
    print(f"✓ {check} — {detail}")


# --- 1. The two string tables must match, key for key.


def check_strings_parity() -> None:
    kotlin = KOTLIN_STRINGS.read_text()
    swift = SWIFT_STRINGS.read_text()

    pairs = [
        ("plain", set(re.findall(r"^\s*val (\w+): String", kotlin, re.M)),
         set(re.findall(r"^\s*var (\w+): String", swift, re.M))),
        ("formatted", set(re.findall(r"^\s*fun (\w+)\(", kotlin, re.M)),
         set(re.findall(r"^\s*func (\w+)\(", swift, re.M))),
    ]

    clean = True
    for kind, in_kotlin, in_swift in pairs:
        for missing, where, other in (
            (sorted(in_kotlin - in_swift), "Swift", "Kotlin"),
            (sorted(in_swift - in_kotlin), "Kotlin", "Swift"),
        ):
            if missing:
                clean = False
                fail(
                    f"string parity ({kind})",
                    f"missing from {where}: {', '.join(missing)}",
                    "the two apps say the same things in the same order; a key on one "
                    "side only ships an English word onto a Kannada screen, or a "
                    "compile error nobody sees until CI",
                )
    if clean:
        total = len(pairs[0][1]) + len(pairs[1][1])
        ok("string parity", f"{total} keys identical across both platforms")


# --- 2. Every plain Swift string must be in the test that proves it is translated.


def check_localization_registration() -> None:
    declared = set(re.findall(r"^\s*var (\w+): String", SWIFT_STRINGS.read_text(), re.M))
    registered = set(re.findall(r"\\\.(\w+)", SWIFT_LOCALIZATION_TESTS.read_text()))
    missing = sorted(declared - registered)

    if missing:
        fail(
            "localization coverage",
            f"not in LocalizationTests.everyString: {', '.join(missing)}",
            "that list is the only thing standing between a missing Kannada "
            "translation and a shipped English word on a Kannada screen — a "
            "reviewer who does not read Kannada skims straight past it",
        )
    else:
        ok("localization coverage", f"all {len(declared)} plain strings registered")


# --- 3. What the bill form collects must reach the store on both platforms.


#: Every field the sell screen hands to `saveBill` / `updateBill`. Each name must
#: be passed the same number of times on both platforms — twice for a field both
#: calls take, once for one only `saveBill` takes.
BILL_FIELDS = [
    "lines", "customer", "paid", "amount", "createdAt",
    "invoiceNo", "photoIds", "note", "discountPercent",
]


def check_bill_fields_reach_the_store() -> None:
    """
    The discount was collected by the Android form, previewed correctly on it,
    and then never passed to `saveBill`. Every parameter of that function carries
    a default, so nothing complained: the shop typed 10%, watched 100 become 90,
    saved, and got a bill for 100.

    Nothing else could have caught it. `android/core` has the rule and tests it;
    the missing argument is in `android/app`, which does not compile here; and
    iOS, which had it right, has no test that reads an Android file. It is a
    named argument counted in two files, which is the only reason this check is
    allowed to exist.
    """
    kotlin = KOTLIN_SELL_SCREEN.read_text()
    swift = SWIFT_SELL_SCREEN.read_text()

    clean = True
    for field in BILL_FIELDS:
        # `= value` in Kotlin, `: value` in Swift. Case-insensitive because Swift
        # spells the one about photographs `photoIDs`.
        in_kotlin = len(re.findall(rf"\b{field}\s*=[^=]", kotlin, re.I))
        in_swift = len(re.findall(rf"\b{field}\s*:", swift, re.I))

        if in_kotlin != in_swift or in_kotlin == 0:
            clean = False
            fail(
                "bill fields reach the store",
                f"{field}: passed {in_kotlin}× on Android, {in_swift}× on iOS",
                "a field the form collects and does not hand over is invisible — "
                "the screen shows the right figure, the store saves the old one, "
                "and every parameter has a default so nothing fails to compile",
            )
    if clean:
        ok("bill fields", f"all {len(BILL_FIELDS)} reach saveBill on both platforms")


def main() -> int:
    print("Stockbook — cross-platform checks\n")
    check_strings_parity()
    check_localization_registration()
    check_bill_fields_reach_the_store()

    if failures:
        print("\n" + "\n\n".join(failures))
        print(f"\n{len(failures)} problem(s). None of these are visible on a screen.")
        return 1

    print("\nAll clear. Now run: cd android && ./gradlew :core:test")
    return 0


if __name__ == "__main__":
    sys.exit(main())
