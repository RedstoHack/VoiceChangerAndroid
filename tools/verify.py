#!/usr/bin/env python3
"""Проверки проекта без Android SDK: манифест, ресурсы, строки. Запускается и в GitHub Actions перед сборкой."""
import glob, os, re, sys, xml.dom.minidom

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
base = os.path.join(root, 'app', 'src', 'main')
errors = []

def err(msg): errors.append(msg)

# ---- XML корректен
for f in glob.glob(base + '/res/**/*.xml', recursive=True) + [base + '/AndroidManifest.xml']:
    try: xml.dom.minidom.parse(f)
    except Exception as e: err('XML не разбирается: %s (%s)' % (os.path.relpath(f, root), e))

# ---- манифест
man = open(base + '/AndroidManifest.xml', encoding='utf-8').read()
if re.search(r'<uses-permission[^>]*android\.permission\.INTERNET', man): err('В манифесте есть INTERNET — приложение должно быть офлайн')
for p in ('RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MICROPHONE', 'POST_NOTIFICATIONS'):
    if 'android.permission.' + p + '"' not in man: err('В манифесте нет разрешения ' + p)
for comp in ('.App', '.MainActivity', '.ShareActivity', '.LiveService', '.ShareProvider'):
    if 'android:name="%s"' % comp not in man: err('В манифесте не объявлен компонент ' + comp)
    elif not os.path.exists(base + '/java/voice/changer/app/%s.java' % comp[1:]): err('Нет файла класса ' + comp)
if 'foregroundServiceType="microphone"' not in man: err('У LiveService нет foregroundServiceType=microphone')
for act in ('PROCESS_TEXT', 'android.intent.action.SEND"', 'android.intent.action.VIEW'):
    if act not in man: err('В манифесте нет фильтра ' + act)

# ---- ссылки на ресурсы
defs = set()
for f in glob.glob(base + '/res/values*/*.xml'):
    t = open(f, encoding='utf-8').read()
    for tag in ('color', 'string', 'style'):
        for n in re.findall(r'<%s name="([\w.]+)"' % tag, t): defs.add((tag, n))
for d in glob.glob(base + '/res/drawable*/*.xml'): defs.add(('drawable', os.path.basename(d)[:-4]))
for d in glob.glob(base + '/res/mipmap*/*.xml'): defs.add(('mipmap', os.path.basename(d)[:-4]))
refs = set()
for f in glob.glob(base + '/res/**/*.xml', recursive=True) + [base + '/AndroidManifest.xml']:
    for m in re.finditer(r'@(?!android:)(\w+)/(\w+)', open(f, encoding='utf-8').read()): refs.add((m.group(1), m.group(2), os.path.relpath(f, base)))
for k, n, f in sorted(refs):
    if (k, n) not in defs: err('Нет ресурса @%s/%s (нужен в %s)' % (k, n, f))
src = ''
for f in glob.glob(base + '/java/**/*.java', recursive=True): src += open(f, encoding='utf-8').read()
for k, n in sorted(set(re.findall(r'R\.(string|drawable|mipmap|color)\.(\w+)', src))):
    if (k, n) not in defs: err('Код использует R.%s.%s, которого нет' % (k, n))

# ---- строки: одни и те же ключи на всех языках, одинаковые подстановки
def load(p): return dict(re.findall(r'<string name="(\w+)"[^>]*>(.*?)</string>', open(p, encoding='utf-8').read(), re.S))
en = load(base + '/res/values/strings.xml')
for p in glob.glob(base + '/res/values-*/strings.xml'):
    other = load(p); lang = os.path.basename(os.path.dirname(p))
    for k in set(en) ^ set(other): err('Строка %s есть не во всех языках (%s)' % (k, lang))
    for k in set(en) & set(other):
        fm = lambda s: sorted(re.findall(r'%\d\$[-+#0 ,(]*\d*(?:\.\d+)?[sdf]', s))
        if fm(en[k]) != fm(other[k]): err('Подстановки в %s различаются (%s)' % (k, lang))
for k, v in en.items():
    if re.search(r"(?<!\\)'", v): err('Неэкранированный апостроф в строке ' + k)

if errors:
    print('ПРОВЕРКА НЕ ПРОЙДЕНА:')
    for e in errors: print(' -', e)
    sys.exit(1)
print('Проверка проекта пройдена: манифест, %d ресурсов, строки на всех языках' % len(defs))
