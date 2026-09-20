# Termux-Æther

[![Build](https://github.com/wallentx/termux-aether/actions/workflows/debug_build.yml/badge.svg?branch=dev)](https://github.com/wallentx/termux-aether/actions/workflows/debug_build.yml?query=branch%3Adev)
[![Tests](https://github.com/wallentx/termux-aether/actions/workflows/run_tests.yml/badge.svg?branch=dev)](https://github.com/wallentx/termux-aether/actions/workflows/run_tests.yml?query=branch%3Adev)

A Termux fork for modern Android: a Pacman-based terminal, a bundled Linux/glibc
compatibility runtime, and an optional hardware-virtualized Arch workspace.
Development and device testing focus on the **Pixel 11 Pro XL running Android 17**.

Built on [Termux](https://github.com/termux/termux-app) and
[Termux Monet](https://github.com/HardcodedCat/termux-monet), with downstream
compatibility fixes and measured terminal optimizations. This is an independent
fork; report fork-specific issues [here](https://github.com/wallentx/termux-aether/issues).

## What sets it apart

- **Modern Android support.** Target SDK 37, compiled against SDK 37.2, with
  app-private command execution adapted to modern Android restrictions. Includes
  Android 17 text-selection fixes, terminal redraw and session-drawer fixes,
  keyboard adjustments, and an in-app **Keep screen on** option.
- **Pacman by default.** Fresh installations use the Termux-Pacman bootstrap and
  Android-compatible package feed. Existing APT installations are not silently
  converted. Pacman here manages Termux packages; it does not turn Android into Arch.
- **Native Linux binary compatibility.** The bundled **Aether** runtime runs
  supported dynamically linked Linux ARM64/glibc programs directly from Termux,
  without PRoot, Arch, or Shizuku. It provides Android-backed DNS, common
  certificate-path mappings, device identity, and mixed Linux/Android child-process
  handling. A complete Geekbench CPU run has passed in the native app context.
- **An optional real Arch VM.** The matching API companion runs Arch Linux ARM
  through Android's virtualization framework. Open it with `Æ`, or run commands
  with `æ`. Persistent storage, networking, selected-directory sharing, on-demand
  startup, optional suspension, disk growth, and live memory adjustment are available.
- **Faster terminal hot paths.** Bulk ASCII parsing, buffer fills/copies, sixel
  decoding, and bitmap copying reduce work in common text and image operations.
  Sixel support is inherited and extended; it is not exclusive to this fork.

## What comes with it

| Component | Included or separate? |
| --- | --- |
| Terminal app, Monet theming, Pacman bootstrap | Included in the default ARM64 APK |
| Aether glibc 2.44 runtime, `aether-run`, and execution probe | Included and installed when Termux opens |
| Device capabilities, thermal diagnostics, Shizuku access, and Arch VM control | Separate [Termux-Æther:API companion](https://github.com/wallentx/termux-aether-api) and [CLI package](https://github.com/wallentx/termux-api-package/tree/wallentx/capabilities) |
| Arch kernel/root filesystem and networking helper | Separate guest artifact and setup; not embedded in the terminal APK |
| Validation scripts and benchmark harnesses | In this repository and CI artifacts; optional tools and Geekbench are not bundled |

Aether leaves package-managed glibc files alone. Some Linux programs need additional
libraries or encounter Android filesystem/syscall restrictions; this is not universal
Linux compatibility. See the [runtime guide](scripts/aether/README.md).

## Measured improvements

Pixel 11 Pro XL, Android 17, September 19, 2026. These are **operation-level
comparisons against the previous implementations in this fork**, not a controlled
whole-app comparison against the latest stock Termux APK.

| Workload | Before | After | Improvement |
| --- | ---: | ---: | ---: |
| Bulk ASCII parsing and buffer updates | 24 MiB/s | 417 MiB/s | 17.4x throughput |
| Colored text parsing and buffer updates | 26 MiB/s | 175 MiB/s | 6.7x throughput |
| Clear a 160x48 simple-text buffer | 108.0 us | 2.71 us | 39.9x faster |
| Sixel decode, repeated spans of 16 pixels | 6.25 ms/image | 0.89 ms/image | 7.0x faster |
| Bitmap growth, 1920x1080 to 1920x1180 | 4.45 ms | 2.72 ms | 39% less time |

Longer sixel repeats reached 63.5–89.9x; single-column sixel, single-byte input,
and Unicode fallback controls were broadly unchanged. These numbers do not imply
matching gains in frame rate, battery life, or arbitrary shell programs. No
hand-written SIMD acceleration is claimed for these Java/Bitmap changes, and no
matched AVF-versus-PRoot speedup has been established.

[Methods, baseline commits, limitations, and raw results](docs/PERFORMANCE.md).

## Install

1. Download the ARM64 APK from a successful
   [Build run on `dev`](https://github.com/wallentx/termux-aether/actions/workflows/debug_build.yml?query=branch%3Adev+event%3Apush).
   GitHub requires sign-in to download Actions artifacts. Normal builds produce
   one `pacman-android-7` ARM64 APK, checksums, and matching Aether sources.
2. Install the APK. The Android package remains `com.termux`, so an update with a
   compatible signature preserves the existing environment. An incompatible
   installation needs a backup and a planned migration; do not uninstall it just
   to try this fork. APK updates do not convert an APT prefix to Pacman.
3. For device integration or Arch, follow the
   [API companion setup](https://github.com/wallentx/termux-aether-api#setup).
   The app and companion must use matching signing certificates. Arch additionally
   needs a supported Android virtualization build, an authorized Shizuku service,
   the CLI wrappers, and the separately staged guest image.

These are development builds, currently using Termux's public debug test key,
not a private release-signing identity. Obtain both APKs from these repositories.
The project name does not change Android package IDs, data paths, or CLI names.

The default build is tailored to ARM64 Pixel testing; other ABIs and APT bootstraps
remain available through [explicit build profiles](docs/BUILD_PROFILES.md).
They are not covered by the Pixel validation results. Builds run in CI, not on
our development phones.

## Try it

Run the included Aether compatibility check from the installed Termux app:

```sh
aether-run "$HOME/../aether/aether-probe"
```

For broader command, storage, and visual sixel checks, see
[device validation](docs/PIXEL11_VALIDATION.md). For implemented features and
remaining experiments, see the [feature plan](docs/PIXEL11_FEATURE_PLAN.md).

## Upstream and licensing

Termux-Æther retains the Termux/Monet foundation and credits its upstream
contributors. The original [upstream README](README.upstream.md) is preserved as
reference; its download links describe upstream builds, not this fork.
See [LICENSE.md](LICENSE.md) for GPLv3 and component exceptions. Aether includes
its runtime license notices; enabled CI builds publish matching source archives.
