# meta-android-platform-tools

OE layer for android-tools from AOSP. See README.md.

- This is general-purpose code, not any one product's. Never mention a
  downstream product, customer, device, internal codename or test unit in
  code, docs, patch headers or commit messages; describe behaviour
  generically ("a debug-unlock token" is fine, a product name is not).

## Generated files: don't edit here

- `recipes-devtools/android-tools/android-tools/build/`,
  `.../android-tools/aosp-patches/` and `android-tools-aosp-patches.inc`
  come from the workbench (github.com/A-Squared-Projects/android-platform-tools-aosp)
  via its `scripts/export-oe-patches.sh`. Change them there, commit there,
  export, commit here.
- `android-tools-aosp-srcrevs.inc` comes from
  `bitbake -c update_aosp_srcrevs android-tools` after changing `AOSP_TAG`
  or `AOSP_PROJECTS`.

## Branches

- `main` is for master (blacksail); `wrynose` for wrynose. Changes land on
  `main` and are merged into `wrynose`.
- `wrynose` differs only where its oe-core requires: `LICENSE` with `&`
  instead of SPDX `AND`, `LAYERSERIES_COMPAT`, and the README note about
  what a wrynose stack needs. Keep it that way; when merging, carry the
  `&` form over any new `LICENSE` line.

## Recipe

- `LICENSE` reflects what's compiled (checked against source headers);
  `LIC_FILES_CHKSUM` points at the fetched sources' own license texts.
  Re-check both after a tag bump or a change to what's built.
- adbd's behaviour is configured through properties (build.prop) and the
  `ADBD_*` variables, not PACKAGECONFIGs that patch code paths.

## Commits

Subject `<recipe or area>: <summary>`; body says what and why as it stands,
not how it was reached. AI-assisted commits end with
`AI-Generated: <model>` and `Signed-off-by: Alex Kiernan <alex.kiernan@gmail.com>`.
Check that both branches parse before pushing.
