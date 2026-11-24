# Model acquisition, conversion and verification

Ringo-Star uses **MediaPipe Tasks GenAI LLM Inference 0.10.18** on Android.
The four `.bin` files are **converted model packages**, not standard
Hugging Face/PyTorch checkpoints. The files used for the academic experiment
are not redistributed. An original model download alone is insufficient to
make the inference feature work.

## Supported model names and upstream sources

| Local `.bin` filename | Source checkpoints | Legacy MediaPipe converter model type |
| --- | --- | --- |
| `gemma2b.bin` | [Gemma 2B](https://huggingface.co/google/gemma-2b) | `GEMMA_2B` |
| `falcon_rw1b.bin` | [Falcon-RW-1B](https://huggingface.co/tiiuae/falcon-rw-1b) | `FALCON_RW_1B` |
| `stable_lm3b.bin` | [StableLM-3B-4E1T](https://huggingface.co/stabilityai/stablelm-3b-4e1t) | `STABLELM_4E1T_3B` |
| `phi2.bin` | [Phi-2](https://huggingface.co/microsoft/phi-2) | `PHI_2` |

Google also offers [preconverted instruction-tuned Gemma 2B packages](https://huggingface.co/google/gemma-2b-it-tflite/tree/main).
Do **not** assume such a package is the same model/quantization as the academic
artifact. Gemma download may require accepting the publisher's access terms.
Review each model's license before accessing, converting or redistributing it.

The legacy four-family converter workflow is documented in Google's
[on-device LLM announcement](https://developers.googleblog.com/large-language-models-on-device-with-mediapipe-and-tensorflow-lite/)
and [Microsoft's converter walkthrough](https://techcommunity.microsoft.com/blog/azure-ai-foundry-blog/bringing-genai-offline-running-slm%E2%80%99s-like-phi-2phi-3-and-whisper-models-on-mobil/4128056).
The latest Google documentation describes newer runtimes; newer conversion
outputs are not automatically compatible with this project's 0.10.18 Android
integration.

## Attempt the historical conversion

1. On a Linux x86-64 or compatible conversion environment with adequate RAM,
   create a Python virtual environment and install **`mediapipe==0.10.18`**:

   ```bash
   python3 -m venv .venv
   . .venv/bin/activate
   python -m pip install "mediapipe==0.10.18"
   ```

   Installation of that historical wheel may require a compatible Python and
   operating system. Other supporting tools for downloading checkpoints, such
   as the Hugging Face CLI, must be installed separately if used.

2. Download the desired **upstream checkpoint and tokenizer** from the official
   model page, accepting any required terms. Record the repository ID and
   exact commit revision. Do not substitute an instruction-tuned checkpoint
   for a base model without recording the change. Keep checkpoints **outside
   the Git repository**.

3. Inspect the checkpoint format and tokenizer. Pass the local weight file or
   a **quoted glob** matching its shards, and the actual tokenizer directory
   or file. Example for a *locally obtained* Phi-2 checkpoint:

   ```bash
   python scripts/convert_model.py \
     --model phi2 \
     --input-ckpt '/path/to/phi-2/model*.safetensors' \
     --vocab-model-file /path/to/phi-2 \
     --format safetensors --backend gpu \
     --work-dir /path/to/local-conversion-workspace/phi2 \
     --output server/ringo_cloud_storage/download/phi2.bin \
     --source-repo microsoft/phi-2 \
     --source-revision YOUR_CHECKPOINT_COMMIT
   ```

   Run this from the repository root. Replace the paths and revision with
   actual values. Choose `--model` from `gemma2b`, `falcon_rw1b`,
   `stable_lm3b`, `phi2`. The script maps each name to the historical
   converter's `model_type` and calls `converter.convert_checkpoint`.
   Use `--format pytorch` for a suitable PyTorch checkpoint, and `--backend
   cpu` only if supported by the target device/runtime. Run the same command
   with `--dry-run` to validate paths and parameters **without converting**.

4. A successful conversion writes the `.bin` and a local
   `.bin.metadata.json` file containing its SHA-256 and provenance fields.
   Keep weights, intermediate data and metadata sidecars out of commits.
   The `.bin` output is installed locally for the server; it and its sidecar
   are **not distributed** in the source repository. The presence of these
   outputs is **not** proof of Android compatibility.

5. Repeat with the exact checkpoint/tokenizer for each other model. Conversion
   requires considerable memory and may fail for individual versions,
   backends or unsupported tokenizer layouts. Known historical converter
   issues exist. Check each converted artifact on the target Android
   runtime and compare binaries and outputs before asserting numerical
   equivalence with the academic experiment.

## Download from the local server to Android

Place compatible binaries at:

```text
server/ringo_cloud_storage/download/
  gemma2b.bin
  falcon_rw1b.bin
  stable_lm3b.bin
  phi2.bin
```

Run `cd server && npm ci && npm start`. The server offers:

```text
GET /api/download/gemma2b
GET /api/download/falcon_rw1b
GET /api/download/stable_lm3b
GET /api/download/phi2
```

A missing or empty file gives HTTP 404. For an example availability check,
use `curl -I http://127.0.0.1:3000/api/download/phi2` (a `200` response
only shows that a file is available, **not** that it can run on Android).

Android's `DownloadManager` stores downloads in the application's external
files Downloads directory. Downloads first use a `.bin.part` filename; the
app checks a configured SHA-256 when present and promotes it to `.bin` only
after the check passes. Download progress can be recovered after the model
selection screen is recreated.

### Configure an independent checksum pin

The local converter prints the SHA-256. For third-party packages, calculate
it separately, e.g. `sha256sum model.bin` (Linux) or
`Get-FileHash model.bin -Algorithm SHA256` (Windows PowerShell).

Build Android with a hash embedded **independently of the server response**:

```bash
cd android
./gradlew assembleDebug -PmodelSha256_phi2=YOUR_64_HEX_SHA256
```

Gradle properties `modelSha256_gemma2b`, `modelSha256_falcon_rw1b`,
`modelSha256_stable_lm3b`, `modelSha256_phi2` and corresponding environment
variables `RINGO_MODEL_SHA256_GEMMA2B`, `RINGO_MODEL_SHA256_FALCON_RW1B`,
`RINGO_MODEL_SHA256_STABLE_LM3B`, `RINGO_MODEL_SHA256_PHI2` are supported.
See the repository-root [`.env.example`](../.env.example) for all supported
configuration variables and the README for terminal setup.
Invalid pins are rejected at build configuration time. A nonempty pin is
checked after download and again before inference.

**If no checksum is supplied, the app only checks that the download succeeded
and produced a nonempty file; cryptographic model verification is unavailable.**
A checksum obtained over untrusted HTTP from the same server is not an
independent integrity guarantee. Debug builds default to the emulator's
`http://10.0.2.2:3000`; physical devices need a LAN URL; release builds
require a configured HTTPS URL.

## Compatibility and runtime requirements

Before relying on a converted artifact, open it with MediaPipe 0.10.18 on a
compatible Android device, generate text from a simple known prompt, and
check model-specific tokenizer, memory requirements and runtime stability.
Run the app's questionnaire-to-PDF flow separately. Hardware acceleration
support and memory needs differ substantially across the four model families.

This is a historical academic prototype, not a clinically validated system.
Model weights are obtained and converted separately from this source repository.
