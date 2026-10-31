#!/usr/bin/env python3
"""Verify the actual packaged model bytes, native ABI and ZIP integrity."""
import hashlib
import sys
import zipfile

EXPECTED = {
    'models/shenava_v15_rnnt_int8/encoder.int8.onnx': 'b9d975c1af77002f83897017e3adaaa6510148c6ba332df92b8d281369f2fdd3',
    'models/shenava_v15_rnnt_int8/decoder.int8.onnx': '0adaad326a536dfbb30c67287e909b4e6f0775fccb8c3ca47270890364824947',
    'models/shenava_v15_rnnt_int8/joiner.int8.onnx': 'e441ed265c961ff4a2436aa8bc36638e65849445e7a1f65cf263f0c310593dde',
    'models/shenava_v15_rnnt_int8/tokens.txt': '8e192963f6e666dfa5721e5cbd4710bc1ef592460a45f08cefc94b2db16a6954',
    'models/punctuation47/punct_cap_seg_47lang.onnx': '640d91c06b7cc5b3e065c12a7097188378aad3bc11568ff1d72c4c0a2acb0df4',
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
    assert any(name.endswith('libsherpa-onnx-jni.so') for name in apk.namelist()), 'Missing RNNT runtime'
print('APK ZIP, six model/tokenizer hashes and arm64 runtime: PASS')
