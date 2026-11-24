# Third-party notices

The original project code is licensed under MIT, as stated in the
[README](README.md). The project authors retain copyright in their
contributions. External dependencies, fonts, models and other third-party
materials remain subject to their own terms.
No model weights are redistributed in this repository.

## Android dependencies and bundled resources

- **SQLCipher for Android Community Edition 4.20.0** — Zetetic LLC and
  contributors. BSD-style 3-clause license; the upstream code is resolved from
  Maven Central rather than included as a binary in this source repository.
  Upstream: <https://github.com/sqlcipher/sqlcipher-android>;
  [license text](licenses/SQLCipher-BSD-3-Clause.txt).

- **Android Ruler Picker, version 1.1** — Keval Patel and contributors. The
  bundled `ruler-picker-1.1.aar` binary is provided under `android/app/libs/` to
  avoid changing the UI implementation. Upstream:
  <https://github.com/kevalpatel2106/android-ruler-picker>. Apache License 2.0:
  [license text](licenses/Apache-2.0.txt).
- **Montserrat font family** — The Montserrat Project Authors, SIL Open Font
  License 1.1. Upstream and licensing:
  <https://github.com/google/fonts/tree/main/ofl/montserrat>;
  [license text](licenses/OFL-1.1-Montserrat.txt). The two
  bundled font files are kept in `android/app/src/main/res/font/`.
- Other Android and Node dependencies are resolved through Gradle/Maven and
  npm, respectively, under their upstream licenses. See `gradle/libs.versions.toml`
  and `server/package-lock.json` for exact dependency declarations.

## LLM checkpoint licensing (not included)

| Family | Model source | Upstream terms |
| --- | --- | --- |
| Gemma 2B | <https://huggingface.co/google/gemma-2b> | Gemma Terms of Use; gated access and downstream obligations |
| Falcon-RW-1B | <https://huggingface.co/tiiuae/falcon-rw-1b> | Apache-2.0 for the linked checkpoint |
| StableLM-3B-4E1T | <https://huggingface.co/stabilityai/stablelm-3b-4e1t> | CC BY-SA 4.0 for the linked checkpoint |
| Phi-2 | <https://huggingface.co/microsoft/phi-2> | MIT for the linked checkpoint |

License obligations for converted `.bin` files depend on the particular
upstream checkpoint, revision, tokenizer and any other materials used for
conversion. Confirm the applicable terms for the artifacts you obtain.

## Academic materials and images

`docs/academic-report.pdf` is the academic report by the project authors,
prepared as a public copy with student identifiers and an identifying RDF example
redacted. The unmodified university submission is maintained separately from
this public source repository. The screenshots and RDF diagram in
`docs/images/` were extracted from the project report.
Third-party figures within the academic report may have separate rights;
refer to their original citations. No separate public license is granted for the report or external illustrations
by this notice.

The provenance of some bundled UI icons and logos is not documented in the
accompanying materials; third-party usage rights require separate assessment.
