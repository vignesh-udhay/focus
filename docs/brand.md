# Tellcat brand

What makes Tellcat recognisable, written in one place. D-089 makes the app
native on each platform, so on iPhone its controls, navigation, type and motion
will all differ from Android's. This page is the part that does not: the name,
the promise, the cat, the colours and the voice.

Nothing here is new. Each rule is a decision already taken somewhere else, and
the pointer says where it is argued. If this page and its source disagree, the
source wins and this page is wrong.

---

# The promise

    If you write it down here, you will be told.

The core promise in `PRODUCT.md`, and the line under onboarding's welcome. The store
says it shorter, "Write it down and you will be told.", followed by "Free, no
account, no subscription." The listing and the first screen agree word for word
on purpose, `play-listing.md`.

The tagline is the user's half of the same sentence:

    Just tell it.

You tell it, and it tells you. D-092. It belongs to the brand's own surfaces,
such as a promo film's end card, and sits beside the promise rather than
replacing it. Inside the app the promise is enough, and an empty screen does not
instruct.

**It is a promise about this app, never a swipe at others.** "Actually go off"
was cut from the store copy for reading as a dig at the category. No named
competitor, no claim the app cannot keep on every phone: the listing never says
"never misses", because D-009 found real phones that break delivery,
`play-listing.md`.

---

# The name

**Tellcat**, one word, capital T only. It says what the app does and what it
looks like: it tells you, and the cat is its face. D-081.

    Store title    Tellcat: Tasks & Reminders
    Domain         tellcat.app
    Contact        hello@tellcat.app

---

# The cat

One animal, everywhere the app has a face. It is the brand's main carrier, and
D-077 records why: without it, Tellcat read as a well-made Material sample.

**Its posture says why a screen looks the way it does.** Up on a Today that may
still fill, asleep on an Inbox with nothing left to decide, lying awake over
clear days ahead, curled asleep while a Focus session runs. The pose carries the
meaning; there are no props, except the question mark on the error pose,
because a failed read is not a state a cat can sit in. D-074,
`expressive-components.md`.

**It never celebrates.** No cheering, no confetti, no congratulating an empty
list. The finished-day cat was removed for that, D-076, and the Logbook's cat,
when it is drawn, must not congratulate either. `PRODUCT.md` principle 7.

**It does not move unless something changed.** On Focus it crosses between two
poses when the clock starts or stops, and nowhere does it idle, blink or
breathe. `expressive-motion.md`.

**How it is drawn.** Flat, in three tones: the coat, a mid tone only where
shapes really overlap (tail, paws), and a dark one for the eyes, the nose and
the error pose's question mark. Dash eyes at rest, a small rounded triangle for
a nose, no mouth, no shading on the ears. Every pose shares one scale, so it
stays one animal. In the Android app the three tones bind to the fixed colour
roles by the part's name: one artwork in light and dark, and under dynamic
colour all three follow the wallpaper together.
`EmptyStateMascot.kt`, `expressive-components.md`.

**Its face is the icon.** The launcher icon, the notification's small icon
(D-072) and the welcome head on onboarding are one drawing in three copies, and
`WelcomeMascotTest` and `NotificationIconTest` fail the build if one is redrawn
without the others. A re-export of the mark updates all three.

**Focus has the cat's own symbol.** `ic_focus` is the same head as a 2dp
outline with one dot where its attention is, generated from the launcher's
path. It is the Focus button on Task Details and the Focus notification's
status-bar icon. D-095.

**It is decoration to a screen reader.** The words beside it say what it shows.

---

# The colours

The 1.0 colours, kept by D-091 after a side-by-side with a bluer regeneration.

| Use | Light | Dark |
| --- | --- | --- |
| Accent: filled buttons, the widget's add button | `#5B54A3` | `#CABEFF` |
| Containers, such as the widget's background | `#E5DEFF` | `#403D89` |
| The cat's coat and the icon's face | `#E5DEFF` | `#E5DEFF` |
| The cat's features and the icon's background | `#403D89` | `#403D89` |

All of them come from one seed, `#4F5DFF`. The cat and the icon hold the same
colours in both themes; only the interface around them inverts.

**The icon, the cat and the interface move together, or not at all.** D-091.
A palette change that leaves one behind splits the brand in two, which is the
mistake D-085 nearly shipped.

**A fresh install opens in these colours**, not the wallpaper's, so the first
screen, the store screenshots and the cat look the same on every phone.
Dynamic colour stays one switch away in Settings. D-077.

---

# The voice

Plain, specific, and calm. The app's copy states facts about the app and leaves
the user's feelings alone.

- **An empty screen states a fact and never instructs.** "Tasks without a day
  wait in your Inbox", not "Add a task when you are ready".
  `expressive-components.md`.
- **A failure says what did not happen.** "Your tasks are safe. This is a read
  that failed." Never a connection, since there is no server.
- **Nothing keeps score.** A number may say what something hides, such as
  checklist items left; it never measures how much the user did. D-084.
- **No motivational noise.** No streaks, no "great job", no "you're on fire".
  `PRODUCT.md` principle 7.
- **Sentence case** for every heading and label, on screen and in the store.
- **Short words over clever ones.** "Say when", "Be told", "Capture it".

---

# Same everywhere, native on each

| Same on every platform | Native to each platform |
| --- | --- |
| The name, the promise and the store copy | Components, navigation, gestures, back behaviour |
| The cat: its poses, what each means, how it is drawn | How the cat's tones map onto the system's colour roles |
| The icon artwork, and the Focus symbol drawn from it | Icon formats: adaptive and themed on Android, iOS's own sizes and dark and tinted variants |
| The brand colours, and the rule that they move together | Typeface and type scale: Material's on Android, the system font on iPhone |
| The voice and the words | Motion physics and haptics |
| Completion as the one lively moment, and nothing celebrating | Notifications: the cat silhouette on Android; iPhone shows the app icon itself |

The right-hand column is D-089 and `PRODUCT.md` principle 5. The left-hand column
is this page.

---

# Not decided

Recorded so they are not mistaken for gaps someone should quietly fill.

- **The Logbook's cat.** Its empty state has none, and it needs a pose drawn.
- **How the iPhone app maps these colours** onto iOS's semantic colours. That is
  part of designing the iPhone app, not of this page.
