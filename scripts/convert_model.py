#!/usr/bin/env python3
"""Convert a local HF checkpoint with MediaPipe's legacy 0.10.18 GenAI converter.

The wrapper prepares the converter configuration for a locally obtained
checkpoint and writes provenance metadata for the resulting model binary.
"""

import argparse
import glob
import hashlib
import json
from pathlib import Path

MODELS = {
    'gemma2b': 'GEMMA_2B',
    'falcon_rw1b': 'FALCON_RW_1B',
    'stable_lm3b': 'STABLELM_4E1T_3B',
    'phi2': 'PHI_2',
}


def sha256(file):
    digest = hashlib.sha256()
    with Path(file).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', required=True, choices=MODELS)
    parser.add_argument('--input-ckpt', required=True,
                        help='Local file or quoted glob for checkpoint shards (no automatic download)')
    parser.add_argument('--vocab-model-file', required=True,
                        help='Tokenizer model file or directory accepted by the selected converter')
    parser.add_argument('--format', choices=('safetensors', 'pytorch'), default='safetensors')
    parser.add_argument('--backend', choices=('cpu', 'gpu'), default='gpu')
    parser.add_argument('--output', required=True, type=Path, help='Output .bin path')
    parser.add_argument('--work-dir', required=True, type=Path,
                        help='Directory for intermediate converter files')
    parser.add_argument('--source-repo', default='', help='Source checkpoint repository ID')
    parser.add_argument('--source-revision', default='', help='Source checkpoint commit/revision')
    parser.add_argument('--dry-run', action='store_true', help='Validate paths without importing MediaPipe')
    return parser.parse_args()


def main():
    args = parse_args()
    files = sorted(glob.glob(args.input_ckpt))
    if not files or any(not Path(f).is_file() for f in files):
        raise SystemExit('No matching local checkpoint files; download the weights first')
    if not Path(args.vocab_model_file).exists():
        raise SystemExit('Tokenizer file/directory does not exist')
    if args.output.suffix.lower() != '.bin':
        raise SystemExit('Output filename must end in .bin')
    if args.output.resolve() in {Path(f).resolve() for f in files}:
        raise SystemExit('Output would overwrite an input checkpoint')
    config_args = dict(input_ckpt=args.input_ckpt, ckpt_format=args.format,
                       model_type=MODELS[args.model], backend=args.backend,
                       output_dir=str(args.work_dir.resolve()), combine_file_only=False,
                       vocab_model_file=str(Path(args.vocab_model_file).resolve()),
                       output_tflite_file=str(args.output.resolve()))
    if args.dry_run:
        print(json.dumps({'conversion_config': config_args, 'checkpoint_files': files,
                          'note': 'Dry run: configuration preview only'}, indent=2))
        return
    try:
        import mediapipe as mp
        from mediapipe.tasks.python.genai import converter
    except ImportError as error:
        raise SystemExit('Install mediapipe==0.10.18 in the converter environment') from error
    if mp.__version__ != '0.10.18':
        raise SystemExit(f'Expected mediapipe==0.10.18, got {mp.__version__}')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.work_dir.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        raise SystemExit('Output already exists; choose a new path rather than overwriting a model')
    converter.convert_checkpoint(converter.ConversionConfig(**config_args))
    if not args.output.is_file() or args.output.stat().st_size == 0:
        raise SystemExit('Converter returned without producing a nonempty .bin file')
    metadata = dict(model=args.model, model_type=MODELS[args.model],
                    source_repo=args.source_repo or None,
                    source_revision=args.source_revision or None,
                    media_pipe_version=mp.__version__, backend=args.backend,
                    checkpoint_format=args.format, sha256=sha256(args.output),
                    size_bytes=args.output.stat().st_size,
                    validation='SHA-256 checksum recorded; check compatibility on target Android device')
    sidecar = args.output.with_suffix(args.output.suffix + '.metadata.json')
    sidecar.write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    print(f'Created {args.output}; SHA-256: {metadata["sha256"]}')
    print('Configure the Android modelSha256_<model> Gradle property with this hash.')
    print('Run an actual on-device inference test before treating the artifact as compatible.')


if __name__ == '__main__':
    main()
