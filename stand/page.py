#!/usr/bin/env python3
# Writes the project page's live street from a frame the stand wrote: the boxes as
# little-endian floats (docs/street.bin) and the rest of the frame (docs/street.json);
# copies the shaders next to them, since the page is served from docs/ alone.
#   python3 stand/page.py out/stand/s0_rest.json
import json, struct, sys, shutil, os
src = sys.argv[1] if len(sys.argv) > 1 else 'out/stand/s0_rest.json'
f = json.load(open(src))
boxes = f.pop('boxes')
n = f['count']
# the turning cube and the glowing button are the last two boxes the world draws
f['cube'] = n - 2
f['button'] = n - 1
open('docs/street.bin', 'wb').write(struct.pack('<%df' % len(boxes), *boxes))
json.dump(f, open('docs/street.json', 'w'), separators=(',', ':'))
for name in os.listdir('assets/shaders'):
    shutil.copy(os.path.join('assets/shaders', name), os.path.join('docs/shaders', name))
print('%d boxes, %d bytes' % (n, os.path.getsize('docs/street.bin')))
