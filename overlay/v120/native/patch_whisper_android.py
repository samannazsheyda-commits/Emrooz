from pathlib import Path

jni = Path('examples/whisper.android/lib/src/main/jni/whisper/jni.c')
gradle = Path('examples/whisper.android/lib/build.gradle')

s = jni.read_text(encoding='utf-8')
replacements = [
    ('whisper_full_default_params(WHISPER_SAMPLING_GREEDY)',
     'whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH)'),
    ('params.print_realtime = true;', 'params.print_realtime = false;'),
    ('params.print_timestamps = true;', 'params.print_timestamps = false;'),
    ('params.language = "en";', 'params.language = "fa";'),
]
for old, new in replacements:
    if s.count(old) != 1:
        raise SystemExit(f'Expected exactly one JNI occurrence: {old!r}, found {s.count(old)}')
    s = s.replace(old, new, 1)

anchor = '    params.n_threads = num_threads;\n'
extra = '''    params.no_context = true;\n    params.no_timestamps = true;\n    params.translate = false;\n    params.suppress_blank = true;\n    params.suppress_nst = true;\n    params.temperature = 0.0f;\n    params.beam_search.beam_size = 3;\n    params.beam_search.patience = 1.0f;\n'''
if s.count(anchor) != 1:
    raise SystemExit(f'Expected n_threads anchor once, found {s.count(anchor)}')
s = s.replace(anchor, anchor + extra, 1)

# Guard the intended Persian final-pass settings.
for needle in [
    'WHISPER_SAMPLING_BEAM_SEARCH',
    'params.language = "fa";',
    'params.no_timestamps = true;',
    'params.translate = false;',
    'params.suppress_blank = true;',
    'params.suppress_nst = true;',
    'params.beam_search.beam_size = 3;',
]:
    if needle not in s:
        raise SystemExit(f'Missing patched JNI setting: {needle}')

jni.write_text(s, encoding='utf-8')

g = gradle.read_text(encoding='utf-8')
old_abis = "abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'"
new_abis = "abiFilters 'arm64-v8a'"
if g.count(old_abis) != 1:
    raise SystemExit(f'Expected official ABI list once, found {g.count(old_abis)}')
g = g.replace(old_abis, new_abis, 1)
gradle.write_text(g, encoding='utf-8')

print('WHISPER_ANDROID_FA_PATCH_OK')
