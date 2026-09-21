# ReadMD development guardrails

Before changing application behavior, read these documents in order:

1. `docs/development/stage-04-user-feedback-fixes.md`, especially section 9.
2. `docs/testing.md`.
3. `docs/design-principles.md`.

Keep the current reading-first UI style. Do not reconnect reading pinch zoom to global settings, consume single-finger Markdown scrolling, rerender the whole document for every style update, broaden supported file types, or raise the 2MB limit without a paged/streaming design.

Before handing off a code change, run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Do not add a lint baseline to hide existing failures.
