"""
Builds the tellcat.app site from docs/privacy-policy.md.

Usage: python tools/build_site.py docs/privacy-policy.md <out-dir>

Writes <out-dir>/index.html and <out-dir>/privacy/index.html. Zip the two and
upload them to the "tellcat" Cloudflare Pages project (Direct Upload), which
serves tellcat.app. The converter covers only what the policy uses: headings,
paragraphs, bullet lists, bold, code and email addresses.
"""
import re, html, sys
src = open(sys.argv[1], encoding='utf-8').read().replace('\r\n', '\n')
def inline(t):
    t = html.escape(t, quote=False)
    t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
    t = re.sub(r'`(.+?)`', r'<code>\1</code>', t)
    t = re.sub(r'([\w.+-]+@[\w-]+\.\w+)', r'<a href="mailto:\1">\1</a>', t)
    return t
out, para, items = [], [], []
def flush():
    global para, items
    if para: out.append('<p>' + inline(' '.join(para)) + '</p>'); para = []
    if items: out.append('<ul>' + ''.join('<li>' + inline(i) + '</li>' for i in items) + '</ul>'); items = []
for line in src.split('\n'):
    if line.startswith('# '): flush(); out.append('<h1>' + inline(line[2:]) + '</h1>')
    elif line.startswith('## '): flush(); out.append('<h2>' + inline(line[3:]) + '</h2>')
    elif line.startswith('- '): para and flush(); items.append(line[2:])
    elif line.startswith('  ') and items: items[-1] += ' ' + line.strip()
    elif not line.strip(): flush()
    else: items and flush(); para.append(line.strip())
flush()
STYLE = """<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root { color-scheme: light dark; --accent: #4F5DFF; }
body { font: 17px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; max-width: 40rem; margin: 3rem auto; padding: 0 1.25rem; }
h1 { font-size: 1.9rem; line-height: 1.2; } h2 { font-size: 1.2rem; margin-top: 2rem; }
a { color: var(--accent); } @media (prefers-color-scheme: dark) { :root { --accent: #A8B0FF; } }
footer { margin-top: 3rem; font-size: .9rem; opacity: .7; }
</style>"""
page = lambda title, body: f'<!doctype html><html lang="en"><head><title>{title}</title>{STYLE}</head><body>{body}<footer><a href="/">Tellcat</a> · <a href="/privacy">Privacy</a> · <a href="mailto:hello@tellcat.app">hello@tellcat.app</a></footer></body></html>\n'
import os; os.makedirs(sys.argv[2] + '/privacy', exist_ok=True)
open(sys.argv[2] + '/privacy/index.html', 'w', encoding='utf-8').write(page('Privacy policy · Tellcat', '\n'.join(out)))
home = ('<h1>Tellcat</h1><p>A calm task app for Android whose reminders actually go off. '
        'Free, no account, no subscription.</p><p>Coming soon to Google Play.</p>')
open(sys.argv[2] + '/index.html', 'w', encoding='utf-8').write(page('Tellcat', home))
