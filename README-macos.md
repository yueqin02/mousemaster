# mousemaster on macOS (experimental port)

This branch adds a macOS platform implementation (`mousemaster.platform.mac`)
alongside the original Windows one. The Windows code is untouched and the
Windows build is unaffected (the macOS-only dependency is added under a
mac-activated Maven profile).

## What works

- Low-level keyboard grabbing via a CGEventTap (combos, modes, key eating,
  regurgitation, key repeat) — same engine, same configuration files
- Keyboard-driven mouse move, click, drag, scroll via CGEventPost
- Multi-screen support (CoreGraphics display enumeration, coordinates in
  points, consistent with CGEvent locations)
- App-specific combos (`app=Safari`-style, via NSWorkspace; matches the app's
  localized name instead of a Windows exe name)
- Macros, string typing (Unicode key events), injected-event tagging so
  mousemaster's own output is never fed back into combo processing
- Live configuration reload, all existing configuration syntax

## What is not implemented yet

- **Overlay visuals**: indicator, grid lines, hint labels are not drawn.
  The mode/grid/hint *logic* runs (grid snapping works, positions are
  computed), you just don't see the visuals. The path to implement it:
  the rendering code (`mousemaster.qt`, `mousemaster.renderer`) is pure Qt
  and already cross-platform; `qtjambi-native-macos` is already on the
  classpath; QtManager looks for Qt 6.8 libraries via the
  `MOUSEMASTER_QT_LIB_PATH` environment variable. A `MacOverlay` reusing the
  renderer classes plus a Qt install is what's missing. Note Qt on macOS
  requires the JVM to start with `-XstartOnFirstThread`.
- **Zoom** (Windows Magnification API has no direct macOS equivalent;
  screenshot-based zoom would be the approach)
- **Hints on UI elements** (`interactive-elements`; needs the AX API)
- **Active keyboard layout detection**: defaults to `us-qwerty`. Override in
  your configuration if you use another layout. TIS
  (`TISCopyCurrentKeyboardInputSource`) detection is the follow-up.
- **Active window rectangle** falls back to the active screen (per-window AX
  queries not implemented), so `grid.area=active-window` behaves like
  `active-screen`.
- Single-instance enforcement, cursor hiding beyond CGDisplayHideCursor,
  `hide-console`.

## Requirements

- macOS 13+ (tested on macOS 27 / Apple Silicon)
- JDK 21+
- Accessibility permission for the process that launches mousemaster
  (System Settings > Privacy & Security > Accessibility). On first run
  without it, macOS shows the grant prompt and mousemaster waits up to 120s.

## Run

```sh
JAVA_HOME=/path/to/jdk-21 ./run-mac.sh --configuration-file=configuration/neru.properties
```

Any of the configurations in `configuration/` work (key aliases are
us-qwerty based; visual feedback is limited until the overlay is
implemented).

## Implementation notes

- `MacPlatform` mirrors `WindowsPlatform`: the CGEventTap is a run-loop
  source on the main thread, pumped by `pumpEvents()`
  (`CFRunLoopRunInMode`), the same structure as the Windows message pump.
  Eating a key = returning NULL from the tap callback.
- macOS modifier keys arrive as `flagsChanged` events without an up/down
  direction; `MacPlatform` tracks pressed modifier key codes to derive it.
  Caps lock and fn are passed through.
- `MacVirtualKey` maps Carbon virtual key codes to the Windows set-1 scan
  codes used by `keyboard-layouts.json`; both identify physical positions,
  so all 220 bundled layouts resolve on macOS.
- Injected events carry an event-source user-data signature (same value as
  the Windows `dwExtraInfo` signature) that the tap uses to ignore
  mousemaster's own output.
- Mouse movement accumulates its own position during a move session
  (`beginMove`/`endMove`) because posted events take longer than one
  main-loop tick to be reflected by `CGEventGetLocation`.
