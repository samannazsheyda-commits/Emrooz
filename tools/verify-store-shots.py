#!/usr/bin/env python3
"""Check the actual rendered app backgrounds and exported screenshot dimensions."""
from pathlib import Path
import sys
from PIL import Image, ImageChops
root=Path(sys.argv[1])
for theme,expected in [('light',(247,242,234)),('dark',(13,23,38))]:
    for name in ['01-home','02-notes','03-note','04-archive','05-settings']:
        matches=list(root.rglob(f'{theme}/{name}.png'))
        assert len(matches)==1,(theme,name,matches)
        with Image.open(matches[0]) as screenshot:
            assert screenshot.size==(1080,1920),(matches[0],screenshot.size)
            screenshot=screenshot.convert('RGB')
            assert screenshot.getpixel((540,20))==expected,(theme,name,'status-bar background',screenshot.getpixel((540,20)),expected)
            assert screenshot.getpixel((20,200))==expected,(theme,name,'app background')
    notes=Image.open(next(root.rglob(f'{theme}/02-notes.png'))).convert('RGB').crop((100,700,980,1000))
    archived=Image.open(next(root.rglob(f'{theme}/04-archive.png'))).convert('RGB').crop((100,700,980,1000))
    assert ImageChops.difference(notes,archived).getbbox() is not None,(theme,'Archive capture still shows the prior active-note frame')
print('Ten actual screenshots, full portrait dimensions and matching system-bar/theme backgrounds: PASS')
