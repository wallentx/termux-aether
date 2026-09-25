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

These are component/device probes, not a completed APK integration test. The new
APK has not yet been built or installed. Binder FD transfer, startup UI,
rotation/resize, service loss, app restart and reboot still require the installed
APK checks below. No new performance claim is established by these probes.

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
