---
name: ios
description: Any work on the iOS half of Stockbook — SwiftUI screens, the Swift domain twin, the backup format, or the iOS CI workflow. Use for implementing a feature on iOS, porting one from Android, or diagnosing a failed iOS build.
tools: Read, Edit, Write, Bash, Grep, Glob, WebFetch
---

The project's rules, constraints, traps and verification loop are in
**[`CLAUDE.md`](../../CLAUDE.md)** at the repository root. Read it first; it
applies here unchanged and is the one copy. This file is only what is specific to
iOS.

## Nothing here compiles locally, and that governs everything

There is no macOS, no Xcode and no Swift compiler in this container. **CI is the
only thing that has ever compiled this code**, at five to ten minutes a round
trip — and iOS now builds only on a pull request, so a change pushed straight to
a branch is never built at all.

Of the eight CI failures during the photo feature, seven were here. Every one was
a mechanical mistake that a compiler would have caught in a second. So read
twice, and prefer the shape that already exists in the file over the shape you
would write from memory.

Before pushing, at minimum: `python3 tools/check.py`, and re-read every symbol
you introduced against its declaration — argument labels, return types, and
whether a value is `Data` or `String`.

## Swift traps this repo has actually shipped

- **A default does not make the synthesised decoder tolerate a missing key.** It
  throws. Adding `creditNotes` made every version-2 backup unreadable. An
  *optional* property is tolerated, because the compiler reaches for
  `decodeIfPresent`; a defaulted non-optional is not. That asymmetry is the whole
  trap. Where a type already has a hand-written `init(from:)`, add the line
  yourself — and put the init in an **extension** if the memberwise initialiser
  must survive.
- **`Loc` is main-actor isolated.** Reading it inside a closure that is not —
  `PhotosPicker`'s label, a nested type's method — fails to compile. Read the
  string in the enclosing view and pass it in as a plain `String`.
- **`BackupService.encode` returns `Data` here and `String` in Kotlin.** A test
  ported across without noticing does not compile.
- **Swift 5 language mode: no `@retroactive`.** Wrap the type instead of
  conforming retroactively — `StatementFile` exists for exactly this.
- `Statement.Entry` is an enum with no `default` branch anywhere on purpose.
  Adding a case is *meant* to break every rendering site.

## The project file

Files are picked up by **synchronized folder groups**: adding a `.swift` file
anywhere under `Stockbook/` puts it in the target with no `.pbxproj` edit. Never
hand-edit the file references.

Build *settings* are the exception — `Info.plist` is generated, so keys live as
`INFOPLIST_KEY_*` in **both** `ios/project.yml` and **both** configuration blocks
of `project.pbxproj`. Three places, and they drift: two keys already exist in one
and not the other.

## Twinning

Anything in `Model` or `Store` has a Kotlin counterpart, usually written first.
Port assertion for assertion — `StoreTests` and its siblings are deliberate
ports, and a figure the two disagree on is a bug in one of them. That is how the
paid-in-full divergence was caught.
