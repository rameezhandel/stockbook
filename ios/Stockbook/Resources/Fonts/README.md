# Fonts

Drop `Inter-Regular.ttf` and `Inter-Medium.ttf` here.

They are picked up by the synchronized folder group, copied into the bundle, and
registered at launch by `NocturneType.registerBundledFonts()` — no `Info.plist`
entry and no project edit needed.

Weight 500 (Medium) is the heaviest weight this design uses. Do not add Semibold
or Bold: the handoff is explicit that hierarchy comes from size and space, never
from weight.

Until the files are here, `Font.custom` falls back to the system face at the same
sizes and the app renders correctly — just not in Inter.
