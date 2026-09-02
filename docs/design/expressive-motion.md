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
| The focused task changing on Focus | `stateColor` | Restrained |
| Entering or leaving the Focus session | `focusSession` | Deliberate |
| The Focus session shape, against the estimate | none: derived from the clock | Ambient |
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
when it is triaged, or leaving Anytime when it is completed, is the same event
as a task moving between Today's bands and reads the same way. A list where
rows move and a list where they vanish are two different products.

## The Focus session

[FD] Two different things move on this screen and they are governed by
different rules.

**The transition** into and out of the session is decoration in the strict
sense: it says nothing the two states do not already say. It gets
`focusSession`, and it is skipped entirely under reduced motion.

**The shape** is not motion in this sense at all. It has no animation spec,
because it is not animated: it is a value derived from the clock, resampled
while the session runs and read in the draw phase. Do not "fix" it into a tween.

[FD] The shape is the single exception to the no-shape-morphing rule below, and
it is exceptional on one condition: **it advances against the task's estimate,
or it does not move.** A task with no estimate gets a still shape. The rule the
exception respects is the one that matters — motion has to communicate a state
change — and here the motion *is* the state.

[FD] It is deliberately unreadable as a gauge. A silhouette cannot be read to a
percentage, and a shape that could would invite clock-watching, which is what
this screen exists to prevent.

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

**No shape morphing, with one exception.** The Focus session shape, and only
while it is carrying progress against an estimate. Shape morphing for its own
sake remains banned; `expressive-design-system.md` says Focuslist takes almost
none of what Material 3 Expressive offers here, and that stands. If a second
morph is ever proposed, it has to clear the same bar: what does it tell the
user that nothing else on screen does?

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

[IMPL] The Focus session shape is checked the same way, against a deliberately
short estimate so a full traverse takes minutes rather than most of an hour.
`FocusProgressTest` covers the arithmetic underneath it, which *is* assertable:
the fraction, the clamp at both ends, a missing estimate, and the guarantee that
asking once after thirty minutes equals asking every second for thirty minutes.
