#!/usr/bin/env python3
"""Verify the actual packaged model bytes, native ABI and ZIP integrity."""
import hashlib
import sys
import zipfile

EXPECTED = {
    'models/whisper/ggml-base-persian-q8_0.bin': 'a8bcb0746be5c9741ba8d1c87c89cccb6fa1452e7878275071f5165a7c43f4a7',
    'models/whisper/ggml-tiny.en-q5_1.bin': 'c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b',
    'models/punctuation47/punctuation-int8.onnx': 'fa630cad87cdf398422aee51c66fda532b82dc8164d9653a588277a2ea98adcb',
    'models/punctuation47/spe_unigram_64k_lowercase_47lang.model': '1bc15b6e5fd80dfac9999582ce3efcad2ac1f7cf4e0e9769b329f5de9ca5af47',
}

with zipfile.ZipFile(sys.argv[1]) as apk:
    assert apk.testzip() is None, 'Corrupt APK'
    assert not any(name.endswith('.part') for name in apk.namelist()), 'Partial download packaged'
    abis = {name.split('/')[1] for name in apk.namelist() if name.startswith('lib/')}
    assert abis == {'arm64-v8a'}, f'Unexpected ABIs: {abis}'
    for name, digest in EXPECTED.items():
        with apk.open('assets/' + name) as asset:
            actual = hashlib.file_digest(asset, 'sha256').hexdigest()
        assert actual == digest, f'Checksum mismatch: {name}'
    assert any(name.endswith('libemrooz_whisper.so') for name in apk.namelist()), 'Missing Whisper runtime'
    assert not any('shenava' in name.lower() or 'sherpa' in name.lower() for name in apk.namelist()), 'Obsolete speech model/runtime packaged'
    models = {name.removeprefix('assets/') for name in apk.namelist() if name.startswith('assets/models/') and not name.endswith('/')}
    assert models == set(EXPECTED) | {name + '.sha256' for name in EXPECTED if name.startswith('models/whisper/')}, f'Unexpected model assets: {models}'
    for name, digest in EXPECTED.items():
        if name.startswith('models/whisper/'):
            assert apk.read('assets/' + name + '.sha256').decode().strip() == digest
print('APK ZIP, four model/tokenizer hashes, no Shenava and arm64 Whisper runtime: PASS')
