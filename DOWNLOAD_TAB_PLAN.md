# Download tab (Seerr) — plan

PIN-gated toolbar tab that embeds Seerr (`https://192.168.0.3:8457/`) in a WebView, with TV remote spatial navigation.

## Status (2026-11-19)

Phases 1–2 shipped on `nexus/pin-unlock`. First device test: **works well** — D-pad highlight, OK activate, left-edge opens Seerr sidebar, requests flow usable. BACK can feel a bit clunky (need an explicit remote Back); polish later if needed.

## Spatial navigation

1. Collect visible interactable DOM nodes (Seerr allowlist + broad fallback for login/modals).
2. Record each node’s screen rect.
3. On D-pad, pick the **best candidate in that direction** (axis overlap + distance).
4. Highlight via `data-seerrfocus` (not a CSS class — React would strip classes).
5. On OK/Center, activate (`click` / SPA route / focus input + IME).
6. Re-scan on DOM mutations (MutationObserver).

Native WebView spatial nav alone is unreliable for Seerr; we inject JS.

## Implementation map

| Piece | Location |
|-------|----------|
| Plan | `DOWNLOAD_TAB_PLAN.md` |
| Hardcoded URL / trusted TLS host | `ui/download/SeerrConfig.kt` |
| PIN-gated tab + destination | `MainToolbar.kt`, `Destinations.kt`, `DownloadFragment.kt` |
| WebView + SSL allow for LAN host | `SeerrWebView.kt` |
| Key intercept | `SeerrFocusWebView.kt` |
| Inject / D-pad / IME / BACK bridge | `SeerrSpatialNavigation.kt` |
| Spatial nav script (MIT, Seerr TV) | `assets/seerr/spatnav.js` |
| Label | `strings.xml` → `lbl_download` |

Visibility: toolbar shows **Download** only when `UserPinRepository.isPinRequired` for the current session. Fragment also redirects Home if opened without a PIN.

TLS: Seerr is HTTPS behind a self-signed `CN=nexus-lan-https` cert. WebView proceeds on SSL errors **only** for host `192.168.0.3`.

Credits: `spatnav.js` vendored from [Seerr TV](https://github.com/Vermino/seerr-tv) (MIT, Copyright 2026 Vermino).

## Phases

### Phase 1 — Spike
- [x] Local plan (this file)
- [x] `Download` toolbar button for PIN accounts only
- [x] `DownloadFragment` + WebView
- [x] Hardcoded Seerr URL + host-scoped self-signed TLS
- [x] Loading / error state

### Phase 2 — TV remote
- [x] Forward D-pad / OK into WebView
- [x] Inject spatial-nav script
- [x] BACK: overlay → history → Home
- [x] Text fields: OK opens IME; BACK closes
- [x] Device test on TV remote (first pass OK; BACK a bit clunky)

### Phase 3 — Polish
- [ ] Configurable Seerr URL (preference) instead of hardcode
- [ ] Confirm cookie persistence across app restarts
- [ ] Naming (Download vs Request / Seerr)
- [ ] Optional: smoother BACK / focus handoff to toolbar
- [ ] Optional: reduce Seerr motion for low-power boxes

## Notes
- Seerr HTTPS only on this host (Caddy); HTTP returns 400.
- First visit redirects to `/login` until authenticated in the WebView.
- Left on the leftmost content opens the Seerr sidebar drawer.
