# Shizuku session validation

Normal SDK-29+ Aether sessions require the session UserService. Recovery sessions
retain the legacy launcher. Do not replay failed commands through another backend.

Run `./gradlew test` and build the APK in CI. Focused tests are
`SessionCommandTest` and `SessionStorageTest`. `NativeSessionProbe.java` can be
compiled alongside `SessionNative`, `SessionSignals`, and `SessionCommand`, converted to DEX, and
run through Shizuku's shell UID with the new `libaether-session.so` in a private
temporary directory under `/data/local/tmp`. Its first argument is that directory;
its second is a Termux-private directory containing the same DEX and library for
the app-UID signaling helper. Preserve Android runtime environment variables,
including `BOOTCLASSPATH`. DEX and native libraries must be read-only before loading. Remove only
the probe's own directory afterward. It tests the actual native implementation,
but does not substitute for APK/Binder integration testing. Require the final
`PASS` in `probe.log`, not merely the rish transport exit code.

`StorageProbe.java` exercises the actual Java storage bridge without modifying
the user's shortcuts. Compile it with `SessionStorage` and a fixture-only
`TermuxConstants.TERMUX_HOME_DIR_PATH` under
`$TMPDIR/aether-session-check/storage-home`, then convert it to DEX. Run it in
the normal app context, leave stdin open, and wait for `STORAGE_READY <path>`.
Through Shizuku + `run-as com.termux`, create/read/delete a uniquely named file
under `<path>/Download`. Send a newline to the probe to finish. Use a fresh
fixture home on each run; never compile this probe with the real HOME constant.

Pixel validation on 2026-09-25 (Android 17 / API 37):

| Check | Result |
| --- | --- |
| Focused command/storage JUnit tests | 9 passed |
| New session Java, generated AIDL, terminal-emulator compilation | Passed against Android SDK stubs and Shizuku 13.1.5 |
| Actual native PTY + run-as launcher | Repeated exit 23, UTF-8, PTY EOF, forced exit -9 and background cleanup passed |
| Actual Java storage bridge | Repeat preparation, custom shortcut preservation, run-as create/read/delete passed |
| Installed preload with linker execution disabled | Original Go/Gum and stock-Go-built self-exec/child-process probe passed |

These initial results are component/device probes. No new performance claim is
established by these probes.

Installed APK `1000.0.0+8012086` was checked later on 2026-09-25 after its build
and unit-test workflows passed. The active zsh/Codex process ancestry reports
the Termux UID in `runas_app`, `AETHER_SESSION_BACKEND=shizuku-runas`, and linker
execution disabled. This exercises the installed session path and Binder PTY
transfer, rather than only a separately launched shell probe.

| Installed-session check | Result |
| --- | --- |
| Original Go 1.27.1, Gum and gh executables | Start successfully; interactive Gum selected Beta and exited 0 |
| Original Go compiler/runtime | Pure-Go and cgo builds, argv/identity, spawn/re-exec, native children, prefix shebangs, relative cwd, `go run`, and `go test` passed |
| Storage and API | `~/storage/downloads` and `$EXTERNAL_STORAGE/Download` write/read/delete and `termux-battery-status` passed |
| Temporary directory | Installed launcher lost `TMPDIR`; corrected command builder passed eight unit tests and a real run-as control/fix comparison; fixed APK validation remains pending |
| Foreign script interpreter paths | Stock Go raw exec of `#!/usr/bin/env` failed; rewriting installed scripts with `termux-fix-shebang` passed, including `env -S` |

Go's missing-TMPDIR diagnostic still built successfully with the stock package's
default temporary directory. Subsequent compiler probes explicitly supplied
temporary directories; they do not prove the installed environment bug fixed.
The first `go test` harness invocation used an invalid absolute package import;
rerunning `go test .` from the package directory passed. Evidence is retained in
`~/.local/state/aether-session-implementation/installed-8012086/` on the Pixel.

Rotation/resize, service loss, explicit recovery, forced session closure,
app restart and reboot still require the remaining installed-APK checks below.
Do not stop Shizuku while the active development session depends on it.

### Installed-script shebang repair

The bundled `termux-fix-shebang /path/to/installed-script` rewrites the interpreter
path to the Termux prefix. For example, `#!/usr/bin/env -S sh -e` becomes
`#!/data/data/com.termux/files/usr/bin/env -S sh -e`, preserving `env`'s argument
splitting and PATH lookup. Original Go programs can then launch the script
through their raw exec syscalls without a custom Go runtime.

On 2026-09-25, 45 standalone personal scripts and five installed package commands
were repaired on the Pixel. Backups, hashes and modes are recorded in
`~/.local/state/aether-session-implementation/shebang-repair-20260925T205510Z/manifest.json`.
Every change was checked to affect only the first interpreter path and preserve
the file mode. Personal symlink targets, including source repositories, were
left unchanged; the `npm`/`npx` package symlinks still point to their original
installed targets.

`npm`, `npx`, `gdbus-codegen`, `glib-genmarshal`, and `glib-mkenums` all failed
through the original Go probe before repair and passed afterward, using both
pure-Go and cgo probes. A separate `env -S` fixture preserved spaced and empty
arguments. Package ownership remains unchanged, but these installed file contents
now differ from their package archives; an upgrade can restore the old shebang.
Apply the repair to installed copies after such upgrades. This is not a kernel
path alias or an automatic global interceptor. The tool follows symlinks, so do
not pass source-linked commands unless editing the source file is intended.

On the installed APK, validate:

1. Stop/start and revoke/authorize Shizuku access. New normal sessions must show
   setup when unavailable; only an explicit Recovery shell selection may use the
   legacy context. Existing commands must never be replayed.
2. Confirm `id -Z` reports `runas_app`, then run original Termux Go/Gum, a small
   `go build`/`go run`, and a self-spawning executable. `/proc/self/exe` must name
   the executable, with `TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE=disable`.
3. Check typing, Home/End, Ctrl-C, Ctrl-Z, `bg`, `fg`, rotation/resizing, exit
   status and forced session closure. Other sessions must remain running.
4. Check `termux-battery-status`, HOME/TMPDIR, `$EXTERNAL_STORAGE`, and
   `~/storage/{shared,downloads,documents}` across shell children. Custom storage
   links must be preserved. Literal `/sdcard` paths are not remapped.
5. Restart the app and reconnect Shizuku, checking that storage shortcuts refresh
   and that exited sessions leave no launcher process or held PTY. Verify plugin
   terminal requests fail clearly when the required backend is unavailable.

The storage bridge holds a directory FD in the app process and publishes a
`/proc/<app-pid>/fd/<fd>` link beneath `~/.termux/aether-storage`. This preserves
the app's storage mount across subprocesses that close inherited descriptors.
It does not grant new Android storage permissions or mount over `/sdcard`.

This change routes terminal sessions, including plugin requests for a terminal.
Non-terminal `AppShell` background tasks retain their existing runner. Keep the
installed compatibility packages until those workloads have also been validated.
