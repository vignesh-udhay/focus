# Motion

What moves in Focuslist, how, and why. Read `expressive-design-system.md`
first for the tags and the principles.

Focuslist takes more from Material 3 Expressive here than anywhere else. The
visual language is deliberately restrained, so motion is where the product gets
to have a character.

---

# The Material motion system

[M3] `MaterialTheme.motionScheme` exposes a `MotionScheme` with six animation
specs. It is stable public API, not experimental.

    defaultSpatialSpec()   fastSpatialSpec()   slowSpatialSpec()
    defaultEffectsSpec()   fastEffectsSpec()   slowEffectsSpec()

[M3] The split between the two families is the important part, and Material
states it plainly. Spatial specs are for "animations that may change the shape
or bounds of the component". Effects specs are for "animations that do not
change the shape or bounds of the component. For example, color animation."

**Animating a colour with a spatial spec is a misuse**, not a stylistic choice.

[M3] There are two built-in schemes. Material describes `standard()` as its
scheme "for utilitarian UI elements and recurring interactions", providing "a
linear motion feel", and `expressive()` as the one "for prominent UI elements
and hero interactions", providing "a visually engaging motion feel".

## What the two schemes actually differ by

[M3] Verified against `material3-android-1.5.0-alpha27`:

| Spec | Expressive damping / stiffness | Standard damping / stiffness |
| --- | --- | --- |
| default spatial | 0.8 / 380 | 0.9 / 700 |
| fast spatial | 0.6 / 800 | 0.9 / 1400 |
| slow spatial | 0.8 / 200 | 0.9 / 300 |
| default effects | 1.0 / 1600 | 1.0 / 1600 |
| fast effects | 1.0 / 3800 | 1.0 / 3800 |
| slow effects | 1.0 / 800 | 1.0 / 800 |

The three effects specs are **identical between the two schemes**. Expressive
motion means softer, springier movement of shape and position, and nothing
else. `fastSpatialSpec` at a damping ratio of 0.6 visibly overshoots; that
overshoot is the whole of the expressive feel.

This is worth knowing before reaching for the expressive scheme to make
something feel livelier: if the thing you are animating is a colour, the scheme
makes no difference at all.

[IMPL] `FocuslistTheme` calls `MaterialExpressiveTheme` without passing
`motionScheme`, and that function defaults it to `MotionScheme.expressive()`.
The app therefore already runs on the expressive scheme. Nothing has to be
switched on; the specs simply have to be used.

---

# Focuslist motion tokens

[FD] Composables do not choose springs. They ask for a named intent, and the
token decides. Five tokens cover the whole app.

| Token | Backed by | For |
| --- | --- | --- |
| `completion` | `fastSpatialSpec()` | the checkbox and the row completing |
| `stateColor` | `defaultEffectsSpec()` | colour and alpha changes |
| `listChange` | `defaultSpatialSpec()` | items entering, leaving, moving |
| `reveal` | `defaultSpatialSpec()` | something appearing or expanding |
| `focusSession` | `slowSpatialSpec()` | entering and leaving the Focus session |

[FD] `focusSession` was the fifth, and adding it was a design decision taken
deliberately rather than a spec invented at a call site. `PRODUCT.md` names
focus mode transitions as motion that earns its place, and none of the other
four fit: the screen changes what it is *for*, which is not a list moving, a
colour changing, or a thing appearing.

[FD] The slow spec rather than the default. Every other transition in the app is
something the user passes through and which should get out of the way. This one
is the user settling into a task, and taking a beat over it is the difference
between a mode and a flicker.

[FD] Why tokens rather than direct calls: an animation spec is a design
decision, and design decisions belong in one place. A composable that writes
its own spring is deciding how the product feels, from inside a file about
laying out a row.

[IMPL] Never write a literal spring or tween in a screen or component. If none
of the four tokens fits what you are building, that is a design question, not a
licence to invent a fifth.

---

# What moves

[FD] The complete list. Anything not here does not animate.

| Interaction | Token | Emphasis |
| --- | --- | --- |
| Completing a task | `completion` | **Strongest in the app** |
| Task title colour and strikethrough | `stateColor` | Restrained |
| A task entering, leaving or moving in a list | `listChange` | Mild |
| A task promoted into, or released from, the Focus now card | `listChange` | Mild |
| The Focus now card appearing or leaving | `reveal` | Restrained |
| The Focus now card changing which task it holds | `reveal` | Restrained |
| The Completed disclosure opening and closing | `reveal` | Mild |
| The top app bar | **nothing**: pinned since D-020 | None |
| The reminder health banner | **nothing** | None |
| The focused task changing on Focus | `stateColor` | Restrained |
| Entering or leaving the Focus session | `focusSession` | Deliberate |
| The Focus session shape, starting or resuming | `focusSession` | Restrained |
| The Focus session shape, pausing | `focusSession` | Restrained |
| Pressing a row or button | Material ripple and state layer | Material default |
| Sheets opening and closing | Material default | Material default |
| The undo snackbar | Material default | Material default |
| Moving between destinations | **nothing** | None |

## Completion

[FD] The one place the app is allowed to be lively. It gets `completion`,
backed by the expressive `fastSpatialSpec`, and the overshoot is the point.

`PRODUCT.md` describes the reward as getting the work done and forbids
celebrations, streaks, scores and badges. A spring on the checkbox is how the
product acknowledges the work without any of that. It is a physical response to
a tap, not a prize.

Nothing else in the app is allowed to feel this way. If a second interaction
starts to, completion has stopped being special and the rule has been broken.

## Colour and strikethrough

[FD] `stateColor`, an effects spec, because these change no bounds. The
existing title-colour animation is the correct idea already; it simply needs to
come from the token.

## Lists

[FD] `listChange`, so a completed task visibly travels to the completed band
rather than teleporting. This is what makes Today's ordering legible as an
ordering rather than a reshuffle.

Mild rather than strong: a list of twelve rows springing every time one is
ticked is noise.

[IMPL] Every task collection applies it, not only Today. A task leaving Inbox
when it is scheduled, or leaving Today when it is completed, is the same event
as a task moving between Today's bands and reads the same way. A list where
rows move and a list where they vanish are two different products.

## Today's bands and the Focus now card

[FD] `reveal` had no user until now. It was declared with the other four and
nothing in the table above claimed it, which meant the one token for "something
appearing" was sitting unused while the screen that needed it was undrawn.
Three things claim it here, and it needs no sixth token.

**Promotion is two movements, not one.** When D-012's rule promotes a task, the
row leaves its band on `listChange` and the card arrives on `reveal`. It is not
a container transform from the row into the card, and it must not become one.
The rules below allow exactly one shape morph, the Focus session shape, and a
row growing into a card would be a second. It would also be a lie about the
mechanism: the card is not that row relocated, it is a different component that
happens to be about the same task. Releasing a task back into its band is the
same pair in reverse.

**The card leaves and returns rather than swapping its words.** Completing the
card's task re-runs the rule. Usually nothing else qualifies and the card goes,
on `reveal`. Sometimes another task qualifies and a card is there again, and
that also plays as leave-then-arrive rather than a crossfade of the text inside
a container that never moved. The card is an assertion about one specific task;
replacing the assertion silently, under a container that does not acknowledge
it, is the one reading of this screen that would be wrong.

**The Completed disclosure gets `reveal`**, which is what the token is for: a
group expanding and collapsing in place, with the rows below it moving on
`listChange` as they always do. Mild, not lively. It is a container opening,
not an event.

**The reminder health banner does not animate.** It appears because a check
found something, not because the user did anything, and the rules below forbid
motion that plays without a user action. It is simply present when the screen
composes. Sliding it in would be the app performing its own bad news.

[IMPL] This entry described a component that did not exist for most of the life
of this document, and read convincingly enough that the banner's absence from
Today was taken for a regression rather than a gap. It is built now, under
`docs/decisions.md` D-040, and its list item carries no `animateItem`, which is
what the paragraph above amounts to in code. `today-screen.md` has the rest.

[IMPL] The top app bar does not move. D-020 replaced the 152dp
`LargeFlexibleTopAppBar` with a pinned 64dp bar, and 64dp has nothing to
collapse to. This entry used to describe Material's
`exitUntilCollapsedScrollBehavior` and say it should not be overridden; there is
now no behaviour to override. See `today-screen.md`.

## The Focus session

[FD] One thing moves on this screen, and it moves only when the state changes.

**The shape** is `MaterialShapes.Cookie4Sided` at rest and
`MaterialShapes.Cookie12Sided` while running. It morphs between the two on
`focusSession` when the state changes, and does not move in between.

[FD] **What it says is whether the clock is running. It never says anything
else.** That sentence is the exception to the no-shape-morphing rule below, and
it is the whole of the exception. `docs/decisions.md` D-014 is the entry.

[FD] It used to say more, and the reversal is worth keeping. The shape was a
progress indicator: a determinate walk from `Circle` to `Clover8Leaf` across the
estimate, and a ring of six shapes walked forever when there was no estimate to
walk against, the two cases told apart by the kind of motion the way
`LoadingIndicatorDefaults` tells its determinate and indeterminate lists apart.
It was derived from the clock rather than animated, and deliberately unreadable
as a gauge so that it could not invite clock-watching.

[FD] D-013 then put a readable number on the screen, and from that moment the
shape and the digits were measuring the same quantity. The shape was the worse of
the two at it: it cannot be read to a value, and it publishes nothing to a screen
reader. A second channel that says what the first says, less well, is decoration,
and decoration is the thing the rules below exist to keep out. So it stopped
measuring.

[FD] Three things went with it and all three were gains. The screen no longer
holds a session that never stops moving and therefore never goes idle, which was
a real testing burden `focus.md` had to describe. The shape became assertable,
being a function of one boolean rather than of spring physics over forty-five
minutes. And reduced motion became simple: the shape swaps instead of morphing
and withholds nothing, because the state it expresses is in the button label and
the status line either way.

[M3] The shape principles are explicit that shape is versatile and not semantic,
and warn against assigning a meaning to a particular shape. This sits closer to
that line than the old design did, and stays on the right side of it. Nothing
claims four lobes means stopped in the abstract. What carries the meaning is that
the shape changes when the state changes, and the state is named in text beside
it regardless.

**The transition into the sheet** is the sheet's own entrance, on `focusSession`,
skipped entirely under reduced motion. It is decoration in the strict sense: it
says nothing the state does not already say.

[FD] **There used to be a container transform here**, growing the Start button's
container into the session's shape. Material names that pattern and calls it the
one that creates the strongest relationship between two states, and it replaced a
scale-and-fade, which was itself a correction, since M3 says Android avoids scale
on enter and exit because it implies an elevation change the system does not
have. It is gone because the two states it joined are now one surface with a
shape that changes on it, and a second shape morph is forbidden below.

[IMPL] One lesson outlives it and generalises past this screen. Everything that
appeared or disappeared across that transform was read off how far the container
had grown rather than animated on a spec of its own, because the two settle at
very different rates: at stiffness 1600 against 200, the labels finished swapping
while the container was visibly still the button. Anything that is a function of
a single spatial animation is not a second animation to keep in step; it is part
of the first.

## Navigation

[FD] No transition animation between destinations, for now. The graph is flat,
the bar switches between siblings, and the framework default is already
appropriate. This is a deliberate absence, not an oversight.

## Sheets and the snackbar

[FD] Material defaults, unchanged. There is no Focuslist-specific reason to
override them, and overriding them would be motion for its own sake.

---

# Rules

[FD]

**Do not animate a property unless the animation communicates a state change.**
Movement that carries no meaning is decoration.

**Keep recurring interactions under roughly 400ms**, unless the Material
component defines its own timing, in which case leave it alone. A user
completes tasks dozens of times a day; anything slower becomes a tax.

**Spatial for bounds, effects for colour.** Material's own rule, and the
easiest one to get wrong.

**No decorative motion.** No pulsing, no attention-seeking idle animation,
nothing that plays without the user having done something.

**No shape morphing, with one exception.** The Focus session shape, saying the
one thing it is allowed to say: whether the clock is running. Shape morphing for its own sake remains banned;
`expressive-design-system.md` says Focuslist takes almost none of what Material
3 Expressive offers here, and that stands. If a second morph is ever proposed,
it has to clear the same bar: what does it tell the user that nothing else on
screen does?

This paragraph used to carve out an exemption for growing the Start button into
the session's circle, on the grounds that a rounded rectangle whose corners stay
at half its height is what every Material container transform is. The carve-out
is moot: D-014 removed the transform, so there is nothing to exempt.

**One expressive moment.** Completion. Everything else is restrained, including
the session transition, which is slow rather than lively.

**Respect reduced motion, and know what it covers.** A zero animator duration
scale means transitions do not play. It does not mean information is withheld:
the session shape keeps showing where the session has got to, because freezing
it would answer a request for stillness by removing the answer. `Motion.kt`
exposes `focuslistMotionEnabled()`, observed rather than read once, since the
setting can change while the app is open.

---

# Verification

[IMPL] Motion cannot be verified by the JVM or instrumented suites. Compose UI
tests now run, but spring physics is not meaningfully assertable: a test can
watch a value settle, which says nothing about whether the overshoot reads as
deliberate.

Motion is checked by watching it on the emulator, and by recording a short
capture when a change is worth reviewing. State it that way in reports: motion
was observed, not tested.

[IMPL] The Focus session shape is the exception, and D-014 is why. It is a
function of one boolean, 4-sided at rest and 12-sided while running, so which
shape a state draws is an ordinary assertion rather than something to watch.
What still has to be watched is whether the change registers at a glance, which
is D-014's own reversal condition.

[IMPL] This paragraph used to say the shape was checked against a deliberately
short estimate so that a full traverse took minutes rather than most of an hour.
There is no traverse any more.

`FocusSessionTest` covers the arithmetic underneath the digits, which *is*
assertable: elapsed and remaining, the clamp at both ends, a missing estimate,
the guarantee that asking once after thirty minutes equals asking every second
for thirty minutes, and that resuming a paused session shifts the origin rather
than accumulating a total.

`FocusProgressTest` covered the same ground against a fraction. Both it and
`focusProgress` were deleted with D-014, which left nothing needing a fraction.
