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
:root { color-scheme: light dark; --accent: #4F5DFF; --bg: #FBF8FF; --fg: #1B1B21; }
html { background: var(--bg); color: var(--fg); }
body { font: 17px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; max-width: 46rem; margin: 3rem auto; padding: 0 1.25rem; }
h1 { font-size: 1.9rem; line-height: 1.2; } h2 { font-size: 1.2rem; margin-top: 2rem; }
a { color: var(--accent); } @media (prefers-color-scheme: dark) { :root { --accent: #A8B0FF; --bg: #131318; --fg: #E4E1E9; } }
footer { margin-top: 3rem; font-size: .9rem; opacity: .7; }
.hero { text-align: center; margin-bottom: 2rem; } .hero h1 { margin: .5rem 0 0; font-size: 2.6rem; }
.hero img { border-radius: 22%; } .lead { font-size: 1.35rem; margin: .4rem 0; }
.small { font-size: .9rem; opacity: .75; }
.button { display: inline-block; background: var(--accent); color: #fff; padding: .7rem 1.4rem; border-radius: 999px; text-decoration: none; font-weight: 500; }
@media (prefers-color-scheme: dark) { .button { color: #1b1b2f; } }
.shots { display: flex; gap: 12px; overflow-x: auto; padding: .5rem 0 1rem; scroll-snap-type: x mandatory; }
.shots img { flex: 0 0 auto; width: 220px; height: auto; border-radius: 16px; box-shadow: 0 1px 6px rgba(0,0,0,.18); scroll-snap-align: start; }
@media (min-width: 760px) { .shots img { width: calc((100% - 36px) / 4); } }
</style>"""
page = lambda title, body: f'<!doctype html><html lang="en"><head><title>{title}</title>{STYLE}</head><body>{body}<footer><a href="/">Tellcat</a> · <a href="/privacy">Privacy</a> · <a href="mailto:hello@tellcat.app">hello@tellcat.app</a></footer></body></html>\n'
import os; os.makedirs(sys.argv[2] + '/privacy', exist_ok=True)
open(sys.argv[2] + '/privacy/index.html', 'w', encoding='utf-8').write(page('Privacy policy · Tellcat', '\n'.join(out)))
# The home page. Copy is taken from docs/play-listing.md, so the site and the
# store page never disagree; the images are the listing's own screenshots.
import os
from PIL import Image
STORE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs', 'store')
os.makedirs(sys.argv[2] + '/img', exist_ok=True)
SHOTS = [('screenshot-1-today.png', 'Today, with an overdue task and a reminder at 6 PM'),
         ('screenshot-2-reminder.png', 'A reminder arriving, with Done and Snooze'),
         ('screenshot-3-quick-add.png', 'Quick Add reading "tomorrow 6pm" as it is typed'),
         ('screenshot-4-reminder-health.png', 'Reminder health, with every check passing')]
figures = []
for name, alt in SHOTS:
    out = name.replace('.png', '.jpg')
    Image.open(os.path.join(STORE, name)).convert('RGB').resize((540, 960), Image.LANCZOS)         .save(sys.argv[2] + '/img/' + out, quality=85, optimize=True)
    figures.append(f'<img src="/img/{out}" alt="{html.escape(alt)}" width="270" height="480" loading="lazy">')
Image.open(os.path.join(STORE, 'icon-512.png')).resize((192, 192), Image.LANCZOS).save(sys.argv[2] + '/img/icon.png')
POINTS = [
    ('Reminders built like alarms',
     'Every reminder is scheduled as an exact alarm. It survives the app closed, the screen off, '
     'the device restarted, and the clock or time zone changed. Snooze it or complete the task '
     'straight from the notification.'),
    ('A screen that checks the reminders for you',
     'Reminder health checks notification access, exact alarms and battery restrictions, sends a '
     'test reminder when you ask, and tells you if a real one arrived late.'),
    ('Type it the way you would say it',
     '"Call mum tomorrow 6pm" schedules for tomorrow and sets a 6pm reminder. What was understood '
     'is marked before you save.'),
    ('Your data stays on your phone',
     'No account, no sign-up, no servers. Tellcat does not request internet permission, so the app '
     'itself sends nothing anywhere.'),
]
points = ''.join(f'<h2>{html.escape(t)}</h2><p>{html.escape(b)}</p>' for t, b in POINTS)
JOIN = 'mailto:hello@tellcat.app?subject=Join%20the%20Tellcat%20test'
home = (
    '<header class="hero"><img src="/img/icon.png" alt="" width="96" height="96">'
    '<h1>Tellcat</h1><p class="lead">Write it down and you will be told.</p>'
    '<p>A task app for Android, free, with no account, no subscription and no ads.</p>'
    f'<p><a class="button" href="{JOIN}">Join the test</a></p>'
    '<p class="small">Tellcat is in a closed test on Google Play before its release. '
    'Email to be added, and you will get the link.</p></header>'
    f'<div class="shots">{"".join(figures)}</div>'
    f'{points}'
    f'<p><a class="button" href="{JOIN}">Join the test</a></p>'
)
open(sys.argv[2] + '/index.html', 'w', encoding='utf-8').write(page('Tellcat · Write it down and you will be told', home))
