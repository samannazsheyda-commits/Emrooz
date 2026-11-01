#!/usr/bin/env python3
"""Verify packaged punctuation, absence of bundled speech and actual native ABI."""
import hashlib
import sys
import zipfile

EXPECTED = {
    'models/punctuation47/punctuation-int8.onnx': 'fa630cad87cdf398422aee51c66fda532b82dc8164d9653a588277a2ea98adcb',
    'models/punctuation47/spe_unigram_64k_lowercase_47lang.model': '1bc15b6e5fd80dfac9999582ce3efcad2ac1f7cf4e0e9769b329f5de9ca5af47',
}
with zipfile.ZipFile(sys.argv[1]) as apk:
    assert apk.testzip() is None, 'Corrupt APK'
    assert not any(name.endswith('.part') for name in apk.namelist()), 'Partial download packaged'
    abis = {name.split('/')[1] for name in apk.namelist() if name.startswith('lib/')}
    assert abis == {'arm64-v8a'}, f'Unexpected ABIs: {abis}'
    assert not any(any(old in name.lower() for old in ['whisper', 'shenava', 'sherpa']) for name in apk.namelist()), 'Bundled speech model/runtime must be absent'
    for name, digest in EXPECTED.items():
        with apk.open('assets/' + name) as asset:
            assert hashlib.file_digest(asset, 'sha256').hexdigest() == digest, f'Checksum mismatch: {name}'
    models = {name.removeprefix('assets/') for name in apk.namelist() if name.startswith('assets/models/') and not name.endswith('/')}
    assert models == set(EXPECTED), f'Unexpected model assets: {models}'
print('APK ZIP, punctuation hashes, arm64 ABI and absence of bundled speech: PASS')
