#!/usr/bin/env python3
"""Fail CI if credential containers or private-key blocks enter the source tree."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=root).decode().split('\0')
blocked_suffixes = {'.pk8', '.p7b', '.pem', '.key', '.p12', '.pfx', '.jks', '.keystore', '.apk', '.aab'}
private_block = re.compile(rb'-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}')
failures = []
for name in filter(None, names):
    path = root / name
    if not path.is_file():
        continue
    private_reference = name.replace('\\', '/').startswith('docs/indicator-review/') or path.name == 'INDICATOR_GLYPH_REVIEW.zh-CN.md' or path.name.startswith(('hmi20_', 'yadea_import_image_'))
    if private_reference or '.private' in path.relative_to(root).parts or path.suffix.lower() in blocked_suffixes:
        failures.append(name)
    elif private_block.search(path.read_bytes()):
        failures.append(name)
if failures:
    raise SystemExit('Private reference, credential, or distribution files are forbidden in the public tree:\n' + '\n'.join(failures))
print('Public tree check passed: no known private reference paths, credential containers or private-key blocks.')
