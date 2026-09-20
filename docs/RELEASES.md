# Coordinated Aether releases

All four components use the release tag `v1000.0.0` for this baseline. Android APKs
use versionCode `1000000000`, reserving substantial space above upstream's
versions while retaining room for future Aether updates. Future APK releases
must increment that code. No finite version guarantees upstream can never pass
it. The Pacman packages also have distinct names, so upstream packages do not
silently replace them during ordinary upgrades.

| Repository | Installed component |
| --- | --- |
| [termux-aether-app](https://github.com/wallentx/termux-aether-app) | `com.termux`, terminal, bundled glibc runtime and rish launcher |
| [termux-aether-api](https://github.com/wallentx/termux-aether-api) | `com.termux.api`, Android/Shizuku/AVF bridge |
| [termux-aether-api-package](https://github.com/wallentx/termux-aether-api-package) | `termux-aether-api`, API commands, `Æ`/`æ`, `aether-update` |
| [termux-aether-exec-package](https://github.com/wallentx/termux-aether-exec-package) | `termux-aether-exec`, native executable compatibility |

## Install or upgrade the complete suite

1. Download `termux-aether-suite-aarch64.tar.xz` and its `.sha256` file from the
   [app release](https://github.com/wallentx/termux-aether-app/releases/latest).
   Verify with `sha256sum -c termux-aether-suite-aarch64.tar.xz.sha256`.
2. Extract into an empty directory. On a new device install the app APK first,
   open it and allow its Pacman bootstrap to finish.
3. Inside Termux run `sh /path/to/extracted/install.sh`. `--check` validates the
   bundle without changing packages. Pacman confirms replacement of official
   `termux-exec`/`termux-api` packages and checks dependencies and file ownership.
4. Install the API APK, then the app APK last, using the commands printed by the
   installer. Updating the running terminal may close sessions; finish active
   jobs and shut down Arch cleanly first.

After this first suite installation, `aether-update` downloads and validates the
latest complete bundle and repeats the same upgrade procedure. Use
`aether-update --check` for download/validation only. The native library and CLI
updates still require this command; installing an APK alone does not update
Pacman packages. APK installation always needs Android's confirmation.

The CLI depends on the Aether exec package and on Bash, util-linux, termux-am,
Python and OpenSSH. Pacman uses its configured repositories for missing
dependencies; refresh/update them with `pacman -Syu` if they are stale. This
bundle targets native aarch64 Termux with the Pacman bootstrap. It refuses an APT
prefix instead of converting or replacing it.

Shizuku, its authorization, an Arch guest image, and rclone for directory sharing
remain optional. The bundle does not initialize, replace, migrate or delete an
Arch disk. It does not install another glibc distribution over the app's runtime.

## Preserving data

APK IDs, the shared UID and signing key remain unchanged. Android can update a
matching installed build in place, retaining the home directory, package prefix,
app preferences, credentials and existing VM files. A different signing key
causes Android to reject the update: stop there; **do not uninstall** to bypass
that check. Back up irreplaceable data before any upgrade.

Before a native transaction the installer saves affected existing files and the
installed package list under `~/.local/state/termux-aether/upgrade-*`. This is a
recovery aid, not a full backup or an automatic rollback. Its preload copy is
outside the paths Pacman replaces and remains available if installation stops.
Unowned file conflicts are reported; the installer never force-overwrites them.

The signing key is the same public Termux test key used by these development
builds. Version numbers and checksums do not turn it into a private signing
identity. Download only from these fork releases.

## Releasing

Keep `master` as the untouched upstream-sync branch and release from `dev`.
Increment the two Android versionCodes for each subsequent release. Build both
native packages on aarch64 Termux using their documented packaging scripts;
never install them as a side effect of packaging. Publish their package assets
and tags, and let each APK release workflow build its corresponding tagged code.
Keep the glibc source archive with the app APK.

After all component assets pass validation, use `scripts/suite/bundle.py` with
the asset directory, output directory, version and JSON mapping of all four
repository names to exact commit SHAs. Verify both APK certificates, application
IDs and versionCodes before bundling. Publish the suite archive and checksum
**last** on the app release. Each suite includes matching glibc sources and a
`release.json` recording its source revisions. A missing or mismatched bundle
checksum stops `aether-update` before package installation.
