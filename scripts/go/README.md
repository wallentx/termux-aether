# Aether Go and Gum

Optional native packages for the Android/ARM64 Aether app. They replace `golang`
and `gum` through Pacman, preserving package ownership and the user's GOPATH,
module cache, projects, and Go configuration. They do not require Arch, PRoot,
Shizuku, or a runtime wrapper.

## Changes

- Detect actual system-linker startup using the kernel's `AT_EXECFN`. Correct
  arguments for internally linked Go executables without stripping arguments
  from direct kernel starts or cgo programs whose C startup already adjusts them.
- Make `os.Executable()` identify the Go executable rather than Android's linker.
- Prepare Android ELF and shebang execution before Go's raw fork/exec and Exec
  syscalls. Preserve arguments, child working directories, execution permissions,
  and per-child executable identity. Restrict ELF handling to native ARM64 PIEs
  that explicitly request Android's linker. System executables run directly.
- Rebuild Go, its compiler/tools, and Gum from source. Gum needs rebuilding because
  a Go runtime is included in each executable; updating Go alone cannot repair
  previously built Go applications.

The syscall preparation is specific to Android/ARM64. Explicit chroot and changed
credentials keep the original Go execution path. Glibc and static Linux binaries
still need their own execution environment. Android's C startup replaces a custom
`argv[0]` with the target path; the pure-Go path preserves it. The implementation
recognizes the canonical `/system/bin/linker64` and APEX linker paths.

## Build on the Pixel

Requires the original, signed Pacman `golang-3:1.27.1-0-aarch64.pkg.tar.xz` in the
package cache, Python, Git, Clang, and the Aether exec package. The builder checks
the exact baseline package SHA256 before extracting anything, and pins Gum 2.0.1
to commit `7179388031ae67d7f538d001be87d931f1cf5e28`.

```sh
python scripts/go/build.py "$HOME/.cache/aether-go-build"
```

Use a new staging directory for each build. The builder never writes installed
executables. Private bootstrap copies receive a strictly checked ARM64 entry-point
adjustment solely to start the existing compiler. These copies are not packaged;
all shipped binaries are rebuilt from the patched source. The resulting Go package
includes that source, the build scripts, license, and provenance.

Outputs are two real Pacman archives in the staging directory's `packages/`:

```sh
pacman -U "$HOME/.cache/aether-go-build/packages/termux-aether-golang-1000.0.0-1-aarch64.pkg.tar.xz" \
          "$HOME/.cache/aether-go-build/packages/termux-aether-gum-1000.0.0-1-aarch64.pkg.tar.xz"
```

Accept removal of the conflicting upstream `golang` and `gum` packages. No
`--overwrite` option is required. Fork package names and version `1:1000.0.0-1`
keep ordinary upstream upgrades from silently replacing them. The `provides`
versions accurately describe the included Go 1.27.1 and Gum 2.0.1 releases.

## Validation and rollback

```sh
python scripts/go/validate.py "$PREFIX/lib/go"
go version
gum --version
gum choose Alpha Beta
```

Validation compiles and executes both pure-Go and cgo probes; checks arguments,
executable identity, child processes, re-exec, system commands, shebang arguments,
relative child directories, denied/missing executables, and `go test` execution.
Gum module downloads also exercise Go HTTPS/DNS. Generated completions and the
manpage are packaged directly, avoiding the failing migration-time scriptlets.

For rollback, install the original cached `golang` and `gum` archives together
with `pacman -U`, accepting removal of the two Aether replacements. Keep those
original archives and signatures until the upgrade is verified.
