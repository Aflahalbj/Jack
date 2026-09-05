# Generator blockstate + model Pintu Sel.
#
#   python tools/gen_prison_door_models.py [folder_resources]
#
# 64 varian blockstate dan 16 berkas model terlalu banyak untuk ditulis tangan;
# hasilnya di-commit ke repo, skrip ini cuma untuk membangkitkannya ulang.
import json, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
MOD = 'jackofhearts'
NAME = 'prison_door'
BASE_Y = {'east': 0, 'south': 90, 'west': 180, 'north': 270}

bs_dir = os.path.join(ROOT, 'assets', MOD, 'blockstates')
md_dir = os.path.join(ROOT, 'assets', MOD, 'models', 'block')
mi_dir = os.path.join(ROOT, 'assets', MOD, 'models', 'item')

# --- model ---
for half in ('bottom', 'top'):
    for hinge in ('left', 'right'):
        for open_ in (False, True):
            for locked in (True, False):
                parent = 'minecraft:block/door_%s_%s%s' % (half, hinge, '_open' if open_ else '')
                tex = '%s:block/%s_%s_%s' % (MOD, NAME, half, 'locked' if locked else 'unlocked')
                model = {
                    'parent': parent,
                    # Celah intipnya lubang sungguhan (alpha 0), jadi daun pintu wajib
                    # digambar di layer cutout — kalau tidak, lubangnya jadi hitam pekat.
                    'render_type': 'minecraft:cutout',
                    'textures': {half: tex},
                }
                fn = '%s_%s_%s%s_%s.json' % (NAME, half, hinge, '_open' if open_ else '',
                                             'locked' if locked else 'unlocked')
                with open(os.path.join(md_dir, fn), 'w') as f:
                    json.dump(model, f, indent=2)
                    f.write('\n')

# --- blockstate ---
variants = {}
for facing in ('north', 'south', 'east', 'west'):
    for half, part in (('lower', 'bottom'), ('upper', 'top')):
        for hinge in ('left', 'right'):
            for open_ in (False, True):
                for locked in (True, False):
                    key = 'facing=%s,half=%s,hinge=%s,locked=%s,open=%s' % (
                        facing, half, hinge, str(locked).lower(), str(open_).lower())
                    y = BASE_Y[facing]
                    if open_:
                        y = (y + (90 if hinge == 'left' else 270)) % 360
                    entry = {'model': '%s:block/%s_%s_%s%s_%s' % (
                        MOD, NAME, part, hinge, '_open' if open_ else '',
                        'locked' if locked else 'unlocked')}
                    if y:
                        entry['y'] = y
                    variants[key] = entry
with open(os.path.join(bs_dir, NAME + '.json'), 'w') as f:
    json.dump({'variants': variants}, f, indent=2)
    f.write('\n')

# --- model item ---
with open(os.path.join(mi_dir, NAME + '.json'), 'w') as f:
    json.dump({'parent': 'minecraft:item/generated',
               'textures': {'layer0': '%s:item/%s' % (MOD, NAME)}}, f, indent=2)
    f.write('\n')

print('blockstate:', len(variants), 'varian')
