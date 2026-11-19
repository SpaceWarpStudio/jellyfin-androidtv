# Android builds on Nexus (this DevPort project)

This is a **headless** Nexus host. Do **not** use Android Studio, a host JDK/SDK, or an emulator.
Build APKs with the Docker toolchain under `/home/warp/nexus/containers/android-build/`.

## Agent instructions (required)

```text
This is an Android app project on the Nexus server. Build APKs with the host Docker
toolchain — do not install a host JDK/SDK, do not use Android Studio, and do not use
an emulator.

CRITICAL — never attach a long Gradle build to the agent shell:
- Do NOT run build.sh and wait on it in the chat tool (blocking or “background” with
  notify_on_output). If the agent session is cut off, that kills docker/Gradle mid-write,
  corrupts Gradle locks, and the next build fails.
- ALWAYS use the detached monitor so the container outlives this chat:

  /home/warp/nexus/containers/android-build/monitor-build.sh start <project-root> assembleDebug
  /home/warp/nexus/containers/android-build/monitor-build.sh status
  /home/warp/nexus/containers/android-build/monitor-build.sh tail 100

- Poll `status` with short shell calls. Do not block the agent turn for minutes on Gradle.
- If a previous run was killed or you see “Unexpected lock protocol” / corrupt locks:

  /home/warp/nexus/containers/android-build/monitor-build.sh clean-stale-locks <project-root>

Always pass this project’s workspace root explicitly (not HelloNexus or sample paths):

  /home/warp/nexus/containers/android-build/monitor-build.sh start /home/warp/Dev/other/jellyfin-androidtv assembleDebug

Never run ./gradlew on the host. The monitor (and build.sh) use image nexus-android-build:local
(JDK 21, SDK 36).

Logs/status: /srv/android-build/logs/current/
APK on success (name may include a version suffix):
  app/build/outputs/apk/debug/*.apk
Tell the user that path when the build succeeds.

Do not commit secrets or release keystores unless asked.
Do not start an emulator or install to a device unless asked.
```

## Operator / agent quick reference

| Command | Purpose |
|---------|---------|
| `monitor-build.sh start <root> assembleDebug` | Detached build (survives chat cut-off) |
| `monitor-build.sh status` | State + last log lines |
| `monitor-build.sh tail [N]` | Tail the log file |
| `monitor-build.sh stop` | Stop the named container |
| `monitor-build.sh clean-stale-locks <root>` | After a killed run / lock errors |
| `build.sh <root> assembleDebug` | Attached only; OK for a human in a real terminal, **not** for agents |

## Findings (2026-11-19)

What looked like a “broken build” was usually:

1. An agent started `build.sh` / attached `docker run --rm … gradlew` under the agent process tree.
2. The DevPort/Cursor session was cut off or stalled.
3. The build died mid-Gradle → corrupted `*.lock` files under the project `.gradle/` and `/srv/android-build/gradle-cache`.
4. Retries failed on locks; another attached retry repeated the loop.

**The project compiles.** A detached monitored `assembleDebug` completed successfully in ~2m 20s (JDK 21 image, no OOM). Prefer `monitor-build.sh` for all agent-driven builds.

## Input note (PIN / keyboards)

PIN unlock uses an on-screen Compose keypad (remote/DPAD), not the system IME. The app also pretends no hardware keyboard is attached and ignores USB/Bluetooth alphabetic keyboard key events so a keyboard plugged into the device cannot hide soft/on-screen pads or drive login.

Full investigation notes: `.cursor/plans/android-build-investigate.md`.
Host tooling docs: `/home/warp/nexus/containers/android-build/README.md`.
