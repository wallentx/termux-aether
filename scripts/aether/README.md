# Aether native glibc prototype

Build only in CI, using Build > aether_prototype=true. The default APK profile,
SDK 37, and existing rish installation remain available. This is an ARM64 proof
of direct app-UID execution, not a general Linux distribution or sandbox.

The prototype APK contains an executable glibc loader in Android's installed
native-library directory, matching Android-adapted glibc 2.44-0 libraries, and a
Bionic helper. `aether-run PROGRAM [arguments]`
uses the installed loader directly, bypassing the Bionic termux-exec interceptor.
The runtime is installed privately at `$HOME/../aether`; package-managed files
under `$PREFIX/glibc` are unchanged and supply optional dependencies such as
libgcc_s. No Geekbench binary is redistributed.

The glibc preload delegates getaddrinfo to a separate Bionic process, translating
flags and result/error structures. Android resolves these queries for Termux's
UID on its current default network. VPN/Private DNS policy is delegated to the
platform; a VPN transition has not yet been tested. The two libcs never coexist
in one process. File wrappers map `/etc/resolv.conf` to Termux's existing resolver
file and common certificate paths to its CA bundle. Programs with independent
DNS implementations can still use the static resolv.conf contents; they are not
claimed to use Android Private DNS. Reads of `/sys/class/dmi/id/sys_vendor` and
`/sys/class/dmi/id/product_name` map to private files populated from Android's
`Build.MANUFACTURER` and `Build.MODEL` at app startup. This lets Linux programs
report the real device make/model without inventing DMI serial numbers or
motherboard information. It does not change the system's sysfs or CPU features. Static binaries/direct syscalls are outside
this preload's coverage. Do not claim universal `/etc` virtualization.

The preload also makes readlink(/proc/self/exe) report the requested program,
so programs such as Geekbench locate sibling assets. execve, execv, and
posix_spawn of dynamic Linux/AArch64 ELF children are routed through the same
loader. PATH-based `execvp`/`execvpe`/`posix_spawnp`, variadic exec calls,
shebang scripts, and glibc `system()` use the same dispatcher. `/bin/sh`,
`/bin/bash`, and `/usr/bin/env` shebangs map to Termux tools; other interpreters
must exist at their stated paths. `env` shebangs can find an interpreter on PATH.
The launcher itself accepts PATH names and scripts as well as ELF paths.

Android children run with a separate Bionic execution shim plus the launcher's
original Termux preload. Their children can switch back to glibc. The glibc
preload never enters a Bionic process. Child environments preserve ordinary
caller-supplied variables; reserved `AETHER_*` runtime settings and ABI-specific
`LD_PRELOAD`/`LD_LIBRARY_PATH` values are supplied by the dispatcher even with
an explicit custom environment. Runtime variables are not a security boundary.

`execvp`/`execvpe` implement shell fallback for executable text without a
shebang; `posix_spawnp` reports `ENOEXEC` instead. PATH comes from the calling
process (including for `execvpe`), with empty entries denoting its current
working directory. Execution mode bits are checked before invoking a loader.
`system()` preserves wait status, ignores SIGINT/SIGQUIT while waiting, and
handles concurrent calls and deferred thread cancellation.

Limits: spawn executable resolution currently occurs before file actions, so
relative executable/PATH lookup combined with spawn chdir/fchdir actions is not
supported. Use an absolute executable path for such calls. Direct syscalls,
`execveat`/`fexecve`, libc-internal launches such as `popen()`, static executables,
async-signal-safe post-fork execution, and programs that deliberately remove or
replace the shim are not covered. Existing Android syscall and filesystem
constraints still apply. This is not general `/usr` or `/bin` virtualization.

`aether-probe` checks app UID, executable identity, resolv.conf visibility,
Android manufacturer/model files, Android-backed DNS success/failure/numeric cases, PATH/variadic exec and spawn
children, mixed glibc/Bionic scripts, permission/recursion errors, and system()
exit/signal/concurrency behavior.
Run it in a native Termux session, not ADB shell or run-as, to validate SDK-37
execution restrictions. Then validate Geekbench --sysinfo and a real HTTPS
client. A successful probe is not a benchmark or evidence of a performance gain.

## Runtime provenance and source

The binaries were copied from the Pixel's Pacman-installed glibc 2.44-0 package,
whose local package database records PGP validation. `provenance.json` pins every
included ELF SHA-256 and the package recipe revision. CI checks those hashes
before building. The APK retains the loader unstripped so its hash is preserved.

The prototype build MUST publish the accompanying aether-source artifact with
its APK: exact GNU glibc 2.44 source tarball, Termux package recipes/build scripts
at the recorded commit, compatibility sources, copyright/license notices, and
provenance. The prototype APK is not intended for publishing through ordinary
release workflows until equivalent source distribution is wired there.
The preload and launcher sources in this directory use the repository's license;
the glibc libraries retain their upstream licenses. See COPYING.LIB and LICENSES.

## Repeatable installed-runtime validation

Run `python scripts/aether/validate.py --cpu` from a native Termux session, or
`aether-validate --cpu` when the script is installed on PATH. `--probe` selects the
CI-built probe binary; `--geekbench` selects an existing Geekbench binary. The
probe and Geekbench are not downloaded by the runner. Omit `--cpu` for the short
identity/DNS/child-process checks and Geekbench system information.

Reports and logs default to a fresh `~/benchmarks/aether-YYYYMMDD-HHMMSS/`
directory. They include actual UID/SELinux context, exit codes, wall time, and
battery/thermal snapshots before and after the run. Geekbench preview uploads
its CPU results. Run comparisons under matched charging and thermal conditions.

Pixel validation on 2026-09-19: all probes, system information and the full CPU
benchmark passed under app UID 10445 (`untrusted_app`), without rish or an Arch VM.
The CPU run took 414.5 seconds: https://browser.geekbench.com/v7/cpu/402547 . The
phone was charging; battery temperature went from 37.5 C to 39.3 C and Android
reported light throttling at the end. This establishes compatibility, not an
apples-to-apples performance comparison with earlier VM or shell runs.

The expanded child-process dispatcher is under validation; the September 19
Geekbench result above validates the earlier explicit execve/posix_spawn path.
