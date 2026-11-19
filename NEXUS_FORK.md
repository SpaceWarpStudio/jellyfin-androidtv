# Nexus / SpaceWarpStudio fork notes

This tree is a **local fork** of [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) used on the Nexus DevPort host. It is **not** pushed to the upstream Jellyfin org (no write access). Changes live on the SpaceWarpStudio GitHub fork and local branches.

## Source control layout

| Remote / branch | Role |
|-----------------|------|
| `upstream` | Official `jellyfin/jellyfin-androidtv` (fetch / rebase source) |
| `origin` | `SpaceWarpStudio/jellyfin-androidtv` (our push target) |
| `nexus/pin-unlock` | Working branch for PIN unlock + login UX |

Typical update from upstream:

```bash
git fetch upstream
git checkout nexus/pin-unlock
git merge upstream/master   # or rebase, if preferred
git push origin nexus/pin-unlock
```

Build APKs on Nexus with `monitor-build.sh` — see [`ANDROID_BUILD.md`](ANDROID_BUILD.md).

---

## Feature summary

### 1. Local per-user PIN unlock (base commits)

- Optional **device-local** PIN gates use of a saved Jellyfin access token (PIN never sent to the server).
- Admin-managed set / change / clear PIN in settings.
- Debug builds use the **same application id and branding** as release so Nexus sideloads can replace the Play Store app.

### 2. PIN login UX (this follow-up)

- Selecting a PIN-protected user opens a **centered on-screen keypad** immediately (no EditText / system IME step).
- Masked entry shows **white asterisks** in a box above the pad.
- **Auto sign-in** when the entered digits match the stored PIN (no Accept button). Wrong guesses stay silent so PIN length is not advertised.
- D-pad focus starts on **5**; movement **wraps** around the pad edges (e.g. right from 6 → 4, up from 3 → C).
- Shared Compose pad: `app/.../ui/pin/PinPad.kt` (settings reuses it via `SettingsPinPad`).
- Startup PIN screen provides `FocusPersistManager` (required by `focusKey`; missing it caused a crash on PIN accounts).

### 3. Ignore physical keyboards (remote-first)

- Android suppresses the soft IME when a USB/Bluetooth alphabetic keyboard is attached; the old PIN field looked “empty.”
- App pretends no hardware keyboard is attached (`PhysicalKeyboard.wrapContext`).
- Alphabetic keyboard key events are ignored in startup / main / external-player activities; TV remotes (non-alphabetic / DPAD) still work.

### 4. Nexus / DevPort agent build notes

- Documented in `ANDROID_BUILD.md` and a Cursor rule under `.cursor/rules/` (local; may or may not be committed).
- Host tooling (outside this git repo): `/home/warp/nexus/containers/android-build/monitor-build.sh` — detached Docker Gradle builds so agent sessions do not kill mid-build and corrupt locks.

---

## Main code touchpoints

| Area | Paths |
|------|--------|
| PIN crypto / storage | `auth/pin/PinHasher.kt`, `auth/pin/UserPinRepository.kt` |
| Auth gating | `auth/repository/AuthenticationRepository.kt` |
| Login PIN UI | `ui/startup/fragment/UserPinFragment.kt`, `ui/pin/PinPad.kt` |
| Settings PIN | `ui/settings/screen/authentication/SettingsAuthenticationServerUserPinScreen.kt`, `SettingsPinPad.kt` |
| Keyboard policy | `util/PhysicalKeyboard.kt`, `JellyfinApplication.kt`, `StartupActivity.kt`, `MainActivity.kt`, `ExternalPlayerActivity.kt` |

---

## Intentionally not upstream (yet)

These changes are tailored to a headless Nexus sideload + family PIN workflow. Upstream contribution would need a cleaned PR (no Nexus-only docs/paths, design review with Jellyfin Android TV maintainers). Until then, develop and push only on the SpaceWarpStudio fork.
