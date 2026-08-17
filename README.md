# Stockbook

Offline inventory and billing for a small hardware shop. Single user, no
account, no server — everything lives on the phone, and the only way data moves
between devices is a file the owner exports by hand.

Native iOS: SwiftUI, iOS 17+, iPhone only, portrait, dark or light.

## Where things are

| Path | What it is |
| --- | --- |
| [`ios/`](ios/) | The app. Open `ios/Stockbook.xcodeproj` and Run — no dependencies. |
| [`ios/README.md`](ios/README.md) | Architecture: how the layers fit, what is built, what is next. |
| [`project/design_handoff_stockbook/README.md`](project/design_handoff_stockbook/README.md) | **The spec.** Every screen, colour, type size, validation rule and the export/import format. |
| [`project/`](project/) | The original HTML design prototype and the Nocturne design-system stylesheet. |
| [`chats/`](chats/) | The design conversation the app was specified in. |
| [`HANDOFF.md`](HANDOFF.md) | Notes from the design-tool export that produced `project/`. |

## Building

```sh
open ios/Stockbook.xcodeproj      # Xcode 16+
```

Tests: `⌘U`, or

```sh
xcodebuild test -scheme Stockbook \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -project ios/Stockbook.xcodeproj
```

CI runs the same build and tests on every push — see
[`.github/workflows/ios.yml`](.github/workflows/ios.yml).

## Status

The data layer, design system, Today and Items screens, and both bottom sheets
are built. Sell, Receipt, Bills, Settings and first-run setup are scaffolded with
their requirements; `ios/README.md` tracks the list.
