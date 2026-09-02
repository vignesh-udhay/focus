# Focuslist design system

The contract for how Focuslist looks. If you are building or changing a screen,
this file and its two companions decide the colours, type, shape, spacing and
motion you use. Do not invent values; if something you need is not here, say so
rather than choosing.

    expressive-design-system.md   this file: principles and tokens
    expressive-components.md      how each component looks and behaves
    expressive-motion.md          what moves, how, and why

Every rule is tagged:

    [M3]    a Material 3 or Material 3 Expressive capability
    [FD]    a Focuslist decision, ours to make and ours to change
    [IMPL]  how to build it in this codebase

Material guidance and our decisions are tagged apart on purpose. Do not quote a
[FD] rule as though Material required it.

---

# Principles

[FD] All eight.

**Expressiveness comes from motion and hierarchy, not decoration.** Material 3
Expressive offers shape morphing, expressive shapes and playful componentry.
Focuslist takes almost none of it. What it takes is the motion physics and the
emphasized type scale.

**Calm, focused, warm, deliberate.** In that order when they conflict.

**The task is the interface.** Chrome recedes. Task titles are the only text at
full contrast on a list screen.

**Hierarchy through typography, spacing and tonal surface.** Not through
elevation, borders, dividers or cards. The app currently uses no shadow or
tonal elevation anywhere and that is deliberate.

**State never relies on colour alone.** Every state that colour expresses must
also be readable some other way.

**Task completion is the single strongest expressive interaction.** It is the
one moment that gets Material's expressive spatial motion at full strength.
`PRODUCT.md` says the reward is getting the work done; this is where the app
says so.

**Ordinary recurring interactions stay restrained.** Opening a task, switching
a tab, scrolling a list: nothing springs.

**No decorative shapes, gradients, excessive pills, unnecessary elevation, or
gamified effects.** `PRODUCT.md` rules out streaks, points, badges, scores and
celebrations, and the visual language has to rule out their visual equivalents
too.

---

# Colour

## Roles

[M3] Material 3 supplies the role names. [FD] The assignments are ours.

| Use | Role |
| --- | --- |
| Screen background | `surfaceContainerHigh` |
| Task collection | `surfaceContainerLowest` |
| Task title, outstanding | `onSurface` |
| Task title, completed | `onSurfaceVariant` |
| Metadata, section labels, supporting text | `onSurfaceVariant` |
| Overdue date | `error` |
| Destructive menu item | `error` |
| Floating action button | `MediumExtendedFloatingActionButton` default, `primaryContainer` |
| Selected navigation item | `NavigationBarItem` default, the secondary family |
| Navigation bar container | `surfaceContainerLow` |
| Top app bar, at rest | `surfaceContainerHigh`, the same as the page |
| Top app bar, lifted | `surfaceContainerHighest` |

[FD] The collection runs *away* from the page, toward the tonal extreme:
white in light, near-black in dark. The page is the tinted ground and the list
is the thing sitting on it.

The polarity is the point, not just the amount. Material's other convention has
containers climb *toward* the middle as they rise, which is what this used
before: the collection was darker than the page in light and lighter in dark.
That is legal and it is what an elevation model implies, but measured against
Material products it is backwards, and at one tonal step it gave 2.0 L\* of
separation in both themes, which is at the floor of what the eye resolves.

[IMPL] Measured against a Google Play reference, in CIELAB:

| | page | collection | separation |
| --- | --- | --- | --- |
| Reference, light | 94.2 | 100.0 | 5.8, collection lighter |
| Reference, dark | 12.3 | 4.0 | 8.3, collection darker |
| Focuslist, light | 94.1 | 100.0 | 5.9, collection lighter |
| Focuslist, dark | 12.1 | 4.0 | 8.1, collection darker |

Both roles are Material's. Nothing here is a literal colour copied from the
reference; only the relationship is.

[FD] Do not add a third surface level to a list screen, and do not put a
container inside the collection.

[IMPL] The resulting stack, with the fallback palette:

| | collection | navigation bar | page and app bar | app bar lifted |
| --- | --- | --- | --- | --- |
| Role | `surfaceContainerLowest` | `surfaceContainerLow` | `surfaceContainerHigh` | `surfaceContainerHighest` |
| Light | 100.0 | 96.1 | 92.0 | 89.9 |
| Dark | 4.0 | 10.1 | 16.9 | 22.0 |

Monotonic in both themes. Measured on the emulator: page to collection 8.1 in
light and 11.8 in dark, page to navigation bar 4.1 and 5.9, selected pill to
navigation bar 6.0 and 19.1.

## Why the chrome moved off its Material defaults

[M3] Material's own defaults are a matched set built around a `surface` page:
the top app bar is `surface`, lifts to `surfaceContainer`, and the navigation
bar is `surfaceContainer`. That puts about 3.8 L\* in light and 5.9 in dark
between the page and the navigation bar.

[IMPL] Moving the page onto a container role broke both halves of that set, and
the order in which they must be fixed is not obvious.

The app bar's `surface` default ended up two tones from the collection and read
as part of it. Giving it the page's own role removes the question: the bar and
the page are one ground, the collection is the only thing floating on it, and
the lift still shows because `scrolledContainerColor` moves one step further
out.

**The navigation bar is not a free variable, and this is the trap.** Its
container is pinned by its own active indicator, not by the page. The indicator
is `secondaryContainer`, which the tonal scheme puts at tone 90 in light;
Material's default bar of `surfaceContainer`, tone 94, is what makes the
indicator visible at all. The first attempt moved the bar out to
`surfaceContainerHighest` to clear the page, which is also tone 90, and the bar
landed on top of its own pill: measured 0.0 apart under dynamic colour and 0.2
in the fallback, separated by nothing but chroma.

The bar therefore stays on the light side of the pill at
`surfaceContainerLow`, and **the page moves instead**, to
`surfaceContainerHigh`. That clears the pill by 6.0 in light and 19.1 in dark
while keeping page to bar at 4.1 and 5.9, which is the separation Material's
own pairing produces.

[FD] The lesson generalises: when a surface role moves, check every component
whose default was chosen relative to it, including that component's own inner
parts. Measure in CIELAB, and check the light theme specifically, because the
light container roles are only two tones apart and collide easily.

## Semantic colour

[FD] `error` is the only semantic colour in the app, and it carries exactly two
meanings: a date that has slipped, and an action that destroys something.
Adding a third meaning to it dilutes both. There is no success colour, no
warning colour, and no per-placement colour.

## Base roles and container roles

[M3] The two families invert differently across themes, and confusing them is
the easiest colour mistake to make.

| Family | Light | Dark |
| --- | --- | --- |
| Base, such as `primary` or `error` | tone 40, strong | tone 80, pale |
| Container, such as `primaryContainer` | tone 90, pale | tone 30, dark |

A base role is strong on a light screen and pale on a dark one, so it stays the
most prominent thing in either theme. A container role does the opposite: pale
on pale, dark on dark, so it never dominates. Neither is wrong; they are for
different jobs.

[FD] `error` is the app's only base-role use, and it earns it: an overdue date
reads strong red in light and pale salmon in dark.

[IMPL] The floating action button was briefly given `primary` for the same
inversion and has been returned to the Material default. Material documents no
base-role floating action button; see `expressive-components.md`.

[FD] A *marker* takes a container role. The selected navigation item is a
marker, not an action: it says where you are and should not compete with the
content, so it keeps Material's container treatment. See the navigation bar
section of `expressive-components.md`, and do not "fix" it to match the button.

[IMPL] This is worth stating because Material's own component defaults do not
always pick the family a given product wants. `MediumExtendedFloatingActionButton`
defaults to `primaryContainer`; Focuslist overrides it. See
`expressive-components.md`.

## Never

[FD] Never write a literal colour in a screen or component. Never read a colour
from anywhere but `MaterialTheme.colorScheme`. Never let colour be the only
signal for completed, overdue, focused or selected.

## Dynamic colour

[M3] Dynamic colour derives the scheme from the user's wallpaper on Android 12
and above.

[FD] It stays on by default. Focuslist is an Android-native product and a
system-coloured task list feels like part of the device.

[FD] Below API 31, and wherever dynamic colour is switched off, the app falls
back to an intentional Focuslist scheme rather than the Material baseline. Both
the light and the dark fallback come from one seed so the two themes are
recognisably the same product.

[FD] The seed is `#4F5DFF`. Its own chroma is unusually high, near 94, and the
primary palette is generated at 48 instead. Carried through at full chroma the
dark scheme's primary container came out a vivid blue that read as loud rather
than calm, which is the opposite of the first principle. The hue is the brand;
the saturation is not.

[IMPL] The current fallbacks are asymmetric: light uses
`expressiveLightColorScheme()` and dark uses `darkColorScheme()`. That is a
known conflict, recorded at the end of this document.

## Light and dark

[FD] What stays identical across themes: every role assignment above, the
surface relationship, strikethrough on completed tasks, `error` on overdue
dates. A screenshot of either theme should be describable by the same sentence.

What may differ: absolute luminance, and the neutral temperature of the
fallback palette.

---

# Typography

[M3] Material 3 Expressive provides thirty type roles: the fifteen standard
ones and fifteen `*Emphasized` counterparts, which render the same size role at
a heavier optical weight. `titleMediumEmphasized`, `headlineMediumEmphasized`
and the rest are available through `MaterialTheme.typography`.

[FD] Emphasized type is used in three places and nowhere else:

| Where | Role |
| --- | --- |
| Focus task title | `headlineMediumEmphasized` |
| Screen titles in the app bar | `titleLargeEmphasized` |
| Empty-state headlines | `titleMediumEmphasized` |

[FD] Everything else uses the standard scale:

| Where | Role |
| --- | --- |
| Task title | `bodyLarge` |
| Task metadata | `bodySmall` |
| Section labels | `labelLarge` |
| Field labels | `labelLarge` |
| Empty-state supporting line | `bodyMedium` |
| Buttons | Material default |

**Why so little emphasis.** Emphasis only reads as emphasis if most things are
not emphasised. The three chosen places are the three moments the app most
wants to be legible at a glance: what am I working on, where am I, and why is
this list empty. Applying it to task titles would make every row shout and none
of them stand out.

[IMPL] `Type.kt` already customises eight roles: `bodyLarge` 16/24,
`bodyMedium` 14/20, `bodySmall` 12/16, `headlineMedium`, `headlineSmall` and
`titleLarge` at SemiBold, `titleMedium` and `labelLarge` at Medium. Keep all of
it. The emphasized roles come from the Material defaults and do not need to be
declared.

## Wrapping and scale

[FD] Task titles wrap to at most two lines and then ellipsize. Metadata wraps
freely onto a second line. A long title must never be allowed to push a list
around.

[FD] Everything must remain usable at 200% font scale. Content that cannot fit
scrolls; it never truncates a value the user needs, and it never puts a
confirming action out of reach.

---

# Shape

[M3] The `Shapes` scale has eight slots. Material's defaults are `extraSmall`
4dp, `small` 8, `medium` 12, `large` 16, `largeIncreased` 20, `extraLarge` 28,
`extraLargeIncreased` 32, `extraExtraLarge` 48. The last three are the
expressive additions.

[M3] `MaterialShapes` provides twenty-eight morphable polygons (Burst, Flower,
Sunny, Puffy and so on) for shape morphing.

[FD] Focuslist uses **none** of them. No expressive polygon, no shape morphing
as an interaction pattern, no shape change for novelty. A task list has nothing
to gain from a button that becomes a flower.

[FD] The Focuslist scale is softer than Material's at the small end and stops
where Material's does at the top:

    extraSmall           8dp
    small               12dp
    medium              16dp
    large               24dp
    largeIncreased      28dp
    extraLarge          28dp
    extraLargeIncreased 32dp
    extraExtraLarge     48dp

The scale must be monotonic. It currently is not: `large` is overridden to 24dp
while `largeIncreased` is left at the Material default of 20dp, so a component
reaching for the larger token gets a smaller radius. Recorded as a conflict
below.

[FD] Shape does not communicate hierarchy in Focuslist. It communicates
component identity: a row is a row, a sheet is a sheet. Two components at
different levels of importance do not get different corner radii to say so.

---

# Spacing and layout

[FD] One 4dp-based scale, already established and unchanged:

    xxs   4dp
    xs    8dp
    sm   12dp
    md   16dp
    lg   24dp
    xl   32dp
    xxl  48dp

[FD] Applications:

| Where | Value |
| --- | --- |
| Screen horizontal margin | `md` |
| Gap between collection rows | `ListItemDefaults.SegmentedGap` |
| First row below the app bar | `xs` |
| Sheet horizontal padding | `md` |
| Sheet bottom padding | `lg` |
| Between sheet fields | `md` |
| Empty-state horizontal margin | `lg` |
| Section label above its group | `md` |
| Section label below the group before it | `lg` |

[IMPL] Never write a raw dimension in a screen or component. Every value comes
from `FocuslistSpacing`, from `MaterialTheme.shapes`, or from a Material
default such as `SegmentedGap`. The codebase currently has zero violations of
this rule outside preview fixtures; keep it that way.

[FD] Dimensions that are not spacing belong in a token too: minimum touch
target 48dp, floating-action-button clearance, and the eventual maximum content
width. Two screens currently compute the FAB clearance separately.

---

# Adaptive layouts

`PRODUCT.md` requires adaptive layouts and says explicitly not to stretch the
phone layout across a tablet. Both halves are built.

[FD] What runs where:

| Width | Navigation | Content |
| --- | --- | --- |
| Compact, under 600dp | Bottom navigation bar | Full width |
| Medium, 600 to 839dp | Navigation rail | Constrained and centred |
| Expanded, 840dp and over | Navigation rail | Constrained and centred |

[FD] 600dp is the boundary, and it is ours. The Material navigation-rail
documentation describes the component fully and gives no breakpoint at all.

[FD] Content is constrained to a maximum width and centred rather than
stretched. A task list eight hundred pixels wide is a worse task list. Sheets
remain bottom sheets at every width. Focus centres its task within the content
column, not the window. No list-detail pane: Task Details is a sheet, and
turning it into a pane would be a product change rather than a layout one.

## The content column

[IMPL] Built, and the whole of it is one function. `focuslistContentGutter()`
returns the padding a screen adds at each side: zero while the window is
narrower than `ContentMaxWidth`, and half the excess once it is wider.

A gutter rather than a width, so one number serves every caller and they all
land on the same column without measuring each other. Lists add it to their
content padding, the empty state and Focus to their own padding, and the
floating action button to its end padding, which is what keeps the button with
the list it adds to instead of against the window edge.

[IMPL] No `WindowSizeClass` and no new dependency. The gutter needs the window
width and nothing else, and `LocalWindowInfo.containerSize` already carries it.
Reading the window rather than the display also measures a split-screen or
freeform window as the app actually sees it.

[FD] A list keeps taking touches across the full window. Only what it draws is
constrained, so a thumb at the edge of a tablet still scrolls.

## The navigation rail

[FD] One navigation model, two presentations. The rail carries the same four
destinations in the same order as the bar, and More stays a menu inside it.
Nothing becomes reachable or unreachable by resizing the window; only where the
control sits changes. That is the whole rule, and it is what makes resizing
safe.

[IMPL] `FocuslistNavigationBar` and `FocuslistNavigationRail` read one
`TopLevelDestinations` list and open one `MoreMenu`. `NavigationBarItem` is a
`RowScope` extension and `NavigationRailItem` is not, so the two cannot share a
single item composable; sharing the data and the menu is as close as the
framework allows, and it is enough to stop them drifting apart.

[FD] The rail container is `surfaceContainerLow`, matching the bar rather than
the rail's Material default of `surface`. The default sits about two tones from
the task collection in both themes, and at the breakpoint the content column
leaves no gutter, so rows would meet the rail at almost the same lightness.
Matching the bar also keeps the active indicator legible, for the same reason
the bar does: the indicator is `secondaryContainer` at tone 90, and the
container has to clear it.

[FD] No header. Material offers the rail's header slot for a floating action
button, but this app's button is extended and carries a text label, and it
belongs to Today and Inbox rather than to the chrome. It stays on the content
column, where it lines up with the list it adds to.

[IMPL] Measured at 1333dp: rail 80.0dp, then equal gutters of 322.7dp either
side of a 608dp column, which is `ContentMaxWidth` less the two `md` margins.
Equal gutters are the thing to check. The column centres inside the area left
over after the rail, not inside the window; centring on the window would put it
half a rail off centre.

[IMPL] That is why `LocalContentWidth` exists. The host measures what remains
beside the rail and publishes it, and `focuslistContentGutter` prefers it over
the window. Everything that lines up on the content column keeps agreeing.

---

# Accessibility

[FD] Rules, all of which the design must satisfy:

- App bar titles carry heading semantics.
- The Focus task title carries heading semantics.
- An empty state's headline carries heading semantics. On an empty screen it
  and the bar title are the only two landmarks there are; the supporting line
  stays unmarked, because it is what the heading leads to rather than a second
  landmark competing with it.
- A task row keeps its click label and its long-press label.
- Overdue is readable from the date text, not only from its colour.
- Interactive targets are at least 48dp.
- The undo snackbar exposes live-region semantics.
- Large font scales stay usable, including at 200%.

[IMPL] Every rule above is now checked by a Compose UI test, at 100% and at
200% font scale, in `androidTest/.../ui/semantics`. The Espresso limitation that
blocked them is gone: espresso-core 3.5.1 reflected for the hidden static
`InputManager.getInstance()`, which the platform removed in Android 14, so
`Espresso.onIdle` threw before any assertion could run. Espresso 3.7.0 reaches
the singleton the way the platform now exposes it.

[FD] What that does and does not establish. The tests assert the semantics tree:
that a title is a heading, that a control publishes its selected state, that the
snackbar sits in a polite live region, that an action carries its label. That is
what an accessibility service reads, so it is the contract worth pinning.

It is not the same as listening to TalkBack. Announcement order, verbosity, and
how a screen reader words what it finds are still unverified, and no test here
can settle them. **Say "semantics are verified", not "TalkBack is verified".**

[FD] Two polite live regions are nested once a snackbar is showing: ours on the
shared host, and one Material's `Snackbar` publishes inside it. Recorded so it
is not mistaken for a bug and quietly removed.

Ours stays. It belongs to the host, which is there whether or not a message is,
and dropping it would leave the behaviour resting on an implementation detail of
a Material component we do not control. A test should assert the message sits
inside *a* polite region, never that there is exactly one; counting regions
encodes Material's internals into our suite.

---

# Design tokens

[IMPL] What belongs in a token rather than in a composable:

| Token group | Holds | Status |
| --- | --- | --- |
| `FocuslistSpacing` | the 4dp scale | exists, unchanged |
| `FocuslistShapes` | the eight corner sizes | exists, needs the monotonic fix |
| `FocuslistTypography` | the eight customised roles | exists, unchanged |
| Fallback colour scheme | light and dark, from one seed | to be built |
| Motion tokens | the four semantic animation specs | to be built, see `expressive-motion.md` |
| Dimension tokens | touch target, row height, FAB clearance, content width | to be built |

[FD] Values that must never be scattered through composables: colours, corner
radii, spacing, animation specs, and any dimension used by more than one
screen.

---

# Conflicts with the current implementation

Known, recorded here so they are not rediscovered.

Items 1 to 5 were resolved when the foundation was built. Items 6 to 8 were
resolved by the retrofit: the placement row now scrolls rather than overflowing,
every screen has adopted the system, and the content column is in place.

What remains:

1. **TalkBack has never been listened to.** The semantics behind it are now
   machine-verified, so what an accessibility service is handed is known. What
   it says out loud, and in what order, is not.
2. **`ui/playground/TaskRowPlayground.kt`** is a leftover harness, wired to
   nothing. Harmless, and not the design system's problem.
