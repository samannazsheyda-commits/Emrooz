#!/usr/bin/env python3
"""Reconstruct a verified, signed release APK without storing a private signing key."""
import base64
import gzip
import hashlib
import json
import sys
from pathlib import Path

source, overlay, destination = map(Path, sys.argv[1:])
patch = json.loads(gzip.decompress(overlay.read_bytes()))
assert patch['version'] == 1
assert hashlib.sha256(source.read_bytes()).hexdigest() == patch['source_sha256'], 'Unexpected source APK'
size = source.stat().st_size
written = 0
destination.parent.mkdir(parents=True, exist_ok=True)
try:
    with source.open('rb') as original, destination.open('wb') as output:
        for operation in patch['ops']:
            if operation[0] == 'bytes':
                data = base64.b64decode(operation[1], validate=True)
                output.write(data)
                written += len(data)
            else:
                assert operation[0] == 'copy' and len(operation) == 3
                offset, remaining = operation[1:]
                assert isinstance(offset, int) and isinstance(remaining, int)
                assert 0 <= offset <= size and 0 <= remaining <= size - offset
                original.seek(offset)
                while remaining:
                    data = original.read(min(remaining, 4 * 1024 * 1024))
                    assert data
                    output.write(data)
                    written += len(data)
                    remaining -= len(data)
            assert written <= patch['target_size']
    assert written == patch['target_size']
    assert hashlib.sha256(destination.read_bytes()).hexdigest() == patch['target_sha256'], 'Release bytes do not match'
except BaseException:
    destination.unlink(missing_ok=True)
    raise
print(destination.name, written, patch['target_sha256'])
