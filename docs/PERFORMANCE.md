# Measured terminal improvements

Measurements were collected on a Pixel 11 Pro XL running Android 17 / API 37 on
September 19, 2026, from native Termux. CI produced the Android benchmark code;
neither phone compiled it locally. The JSON files in
[benchmarks/2026-09-19](benchmarks/2026-09-19) retain the original samples.

## What these comparisons establish

The baselines are the previous implementations in this fork, or an explicitly
retained reference loop. They isolate changes to terminal operations. They are
**not a stock Termux APK versus Termux-Æther APK comparison**, do not measure
battery energy, and do not establish a whole-terminal speedup.

| Comparison | Baseline | Optimized version | Method |
| --- | --- | --- | --- |
| ASCII parsing | `7c2a9409` | `536f96b6` | Old/new/new/old; median of 10 samples per variant |
| Buffer fills and copies | `27cc183d` | `2c36f451` | Old/new/new/old; median of 10 samples per variant |
| Sixel row writes | Former per-pixel writer with the same bounds/cursor handling | `67218de5` | Five alternating samples per implementation |
| Bitmap resize copying | Default Canvas copy | SRC Canvas in `80cc1cdc` | Five samples per method in alternating order |

Each workload was warmed. Text, buffer, and sixel samples ran for at least 150 ms;
bitmap-copy samples ran for at least 200 ms. Clocks, CPU placement, and background
scheduling were uncontrolled. Correctness checks compare pixels or output/state
fingerprints, supplemented by CI regression tests. Small differences in unchanged
fallback paths should be treated as noise.

## Results

| Text workload | Before MiB/s | After MiB/s | Throughput ratio |
| --- | ---: | ---: | ---: |
| ASCII, 64 KiB chunks | 23.97 | 417.31 | 17.41x |
| ASCII, 4 KiB chunks | 23.98 | 416.98 | 17.39x |
| Log lines | 24.05 | 349.89 | 14.55x |
| Colored lines | 26.04 | 174.89 | 6.72x |
| Single-byte input (control) | 23.31 | 23.27 | 1.00x |

Text timing includes parsing, buffer writes, wrapping, and scrolling; it excludes
PTY I/O and display rendering. Mixed Unicode, insert mode, nowrap, and line-drawing
controls were within roughly 0–3% of the baseline. The plain ASCII payload is
65 KiB overall, delivered in the named chunk sizes.

| Buffer workload | Before us/op | After us/op | Speedup |
| --- | ---: | ---: | ---: |
| Clear 80x24 | 27.133 | 0.843 | 32.17x |
| Clear 160x48 | 107.996 | 2.706 | 39.90x |
| Copy 160-column row | 2.409 | 0.058 | 41.78x |
| Copy 160x48 scrolling region | 112.740 | 2.732 | 41.27x |
| Unicode row copy (control) | 72.019 | 71.908 | 1.00x |

Buffer timing excludes allocation, parsing, PTY I/O, and rendering. Some terminal
scrolls rotate row references instead of copying cells, so the region-copy result
does not describe every scroll. Unicode fallback was effectively unchanged.

| Sixel workload | Before ms/image | After ms/image | Speedup |
| --- | ---: | ---: | ---: |
| Single columns (control) | 6.005 | 5.925 | 1.01x |
| Repeats of 16 | 6.252 | 0.892 | 7.01x |
| Repeats of 1024 | 5.825 | 0.092 | 63.51x |
| Repeats of 8192 | 13.620 | 0.152 | 89.87x |
| Overpainting | 11.703 | 0.523 | 22.39x |

Sixel timing includes allocation, palette selection, and bitmap decoding. It
excludes the outer escape parser, PTY transport, and Canvas/GPU rendering. Every
workload passed pixel, dimensions, and cursor-state comparisons. Long repeat spans
benefit most because batching removes thousands of per-pixel calls.

| Bitmap size change | Default Canvas ms | SRC Canvas ms | Time reduction |
| --- | ---: | ---: | ---: |
| 1024x96 to 1124x196 | 0.238 | 0.182 | 24% |
| 1060x508 to 1060x610 | 1.142 | 0.701 | 39% |
| 1060x610 to 972x612 | 1.278 | 0.856 | 33% |
| 1920x1080 to 1920x1180 | 4.453 | 2.720 | 39% |

Bitmap timing includes destination allocation and copy, not terminal parsing or
UI drawing. Pixel equality includes partial alpha. These Java/Bitmap optimizations
do not establish which SIMD instructions Android's runtime or graphics libraries
selected.

## Reproduce and interpret

The unchanged harnesses and run instructions are in
[text-benchmark](../scripts/text-benchmark/README.md),
[buffer-benchmark](../scripts/buffer-benchmark/README.md), and
[sixel-benchmark](../scripts/sixel-benchmark/README.md) (including bitmap copies).
Compare the recorded baseline/candidate commits on the same device; keep all raw
samples and power/thermal metadata. The [raw sample directory](benchmarks/2026-09-19)
contains the four text runs, four buffer runs, sixel comparison, and bitmap comparison.
File names preserve candidate commit IDs and run order labels.

A native Aether Geekbench run demonstrates compatibility, not a measured gain over
stock Termux. Earlier native, shell, and Arch scores had different battery/thermal
conditions. A matched AVF-versus-PRoot comparison remains outstanding. Existing
frame traces also do not justify a general FPS, lower-memory-use, or battery-life
claim; some captures were affected by redraw/rotation problems.
