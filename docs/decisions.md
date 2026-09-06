# Decisions

Why the product is shaped the way it is.

This file exists to stop the app drifting across coding sessions. A session
that has not seen the reasoning behind a cut will helpfully rebuild the thing
that was cut, because most of these features look obviously useful in
isolation. They were cut on evidence, not on taste.

## How to use this file

Before adding anything listed under "Not in this product" in `PRODUCT.md`,
read the decision that removed it.

To reverse a decision, add a new entry that supersedes the old one and says
what new information changed the answer. Do not edit the old entry, and do not
reverse a decision silently in code. Writing the reversal down is the whole
mechanism.

A decision is not reversed by a request that does not mention it. If a task
would quietly undo one of these, say so before implementing.

---

## The evidence base

Decisions D-001 through D-005 rest on a review study run on 4 September 2026:
6,051 one-, two- and three-star reviews plus 1,500 five-star reviews, pulled
from the US English Google Play listings of eleven competing task apps
(Todoist, TickTick, Microsoft To Do, Google Tasks, Any.do, Memorigi,
Tasks.org, Structured, Focus To-Do, Tasks: To Do List & Reminders, To Do
List), classified by keyword and read by hand at the top of the upvote
distribution.

Two limits worth carrying forward. Reviews over-sample the disappointed, so
the ranking of complaints is more trustworthy than the absolute percentages.
And the study is Android-only, because Apple retired its public review feed.

---

## D-001. Focuslist is free, with no purchases of any kind

**Decision.** No subscription, no paid tier, no in-app purchases, no ads, no
account. Not a free tier. No purchases at all.

**Why.** Price is the single largest complaint category in the entire study.
It accounts for 28% to 40% of low-star reviews for every app that charges,
against 1% to 2% for Microsoft To Do and Google Tasks, which do not. Any.do
carries a 7.5% one-star share against Google Tasks' 4.2%, and pricing
complaints are most of that gap.

Shipping free deletes the largest source of negative reviews in the category
before any code is written. It is the cheapest quality decision available.

Subscription resentment specifically is its own cluster: 419 reviews, with 127
asking to pay once instead. One representative line, on a $36/year
competitor: "I might pay $5 as a one time purchase, but that is insane. It's
NOT a service."

**What would reverse this.** Nothing about revenue, because revenue is not a
goal for this project. Only a running cost that makes free impossible, which
local-first storage with no backend is specifically designed to avoid. See
D-006.

---

## D-002. Cut Anytime, Someday, and the manually curated Today

**Decision.** Three destinations: Inbox, Today, Upcoming. Anytime and Someday
are removed. Today is derived from scheduled date, never hand-curated.

**Why.** Inbox / Today / Upcoming / Anytime / Someday is GTD vocabulary. It
works in Things on iOS because Things inherited a decade of GTD-literate Mac
users. Android's audience is broader and does not arrive with that training.

Memorigi is the closest existing app to the original Focuslist plan: a
beautiful, Material-designed, Android-native, Things-shaped task manager. Its
reviewers describe getting lost in exactly this structure:

> "There's something about the app that confuses me a lot. I have a hard time
> remembering which section I saved something, whether my day, tasks, or in a
> specific label. I think it's because they all function the same way and all
> look the same too."

And on manual Today curation, which the reviewer reads as a defect:

> "If you have a task that you set for today in one of your categories, it
> doesn't appear in the today feed. You have to manually move it there. But
> then it disappears out of..."

Three destinations cover everything the five did. Each is explainable in one
sentence without teaching a system.

**What would reverse this.** Real Focuslist users asking for a place to put
undated work, repeatedly and unprompted, after 1.0 has shipped. The answer
then is probably flat Lists (D-003), not the reinstatement of Someday.

---

## D-003. Cut Areas, and defer Projects

**Decision.** Areas are removed entirely. Projects are deferred past 1.0, and
when they return they are called Lists and are flat, with no nesting.

**Why.** A two-level Area -> Project -> Task hierarchy is a large build for
something the review evidence never asks for. It also contradicts principle 4
in `PRODUCT.md`: the app should encourage execution rather than task
administration. Two levels of container is administration.

"Project" and "Area" are also system vocabulary. "List" is the word every
competing app uses and every user already knows.

**What would reverse this.** Post-1.0 demand for grouping. Satisfy it with one
flat level called Lists. A second level of nesting needs its own decision
entry and a real reason.

**Note, 5 September 2026.** The Clean Slate board labels task rows "Work ·
Deep work", "Health · Evening", "Personal · 15m". Read as a grouping, that is
this decision reversed. It is not being read that way. `PRODUCT.md` lists
every property a task may have and none of them is a category, and it parks
flat Lists post-1.0 behind real users asking. The labels are mockup texture,
of the same kind as the invented task titles beside them.

When these screens are built, the row's second line carries what the app
actually holds: the date, the duration, and how often it repeats, which is
what it carries today. Anyone who wants the category to be real should reach
this entry first and write the superseding one.

---

## D-004. Focus stays as it is, and loses the queue

**Decision.** Keep Focus mode, single task, frozen at its current behavior.
Remove the Focus queue. Do not extend Focus before 1.0 ships, and do not lead
with it in the store listing.

**Why.** Focus is the best craft work in the app and the market's smallest
ask. Calendar and time-blocking views appear in 36 of 6,051 low-star reviews,
0.6%. Focus To-Do already holds the tasks-plus-pomodoro position with 10
million installs.

This is not an argument to delete it. Focus is distinctive, it is already
built, and it is a large part of why this app is worth making. It is an
argument about sequencing: it is finished, and further investment in it is
investment not going into reminders.

The queue goes because it multiplies the concept. Focus works on one task.

**What would reverse this.** 1.0 shipping, reminders being genuinely reliable,
and a wish to keep building. Focus is the natural place to spend that time.

---

## D-005. Reminders are the product

**Decision.** The app's job is to reliably interrupt the user at the right
moment. Reminder delivery is held to a higher standard than any other
behavior, including correctness of the UI.

**Why.** Missed, silent, and late reminders are the largest functional
complaint in the study: 657 of 6,051 low-star reviews. Every competitor fails
at it, including Google's and Microsoft's free apps, whose reminder complaint
rates are 45% and 28% of their low-star reviews.

The related asks stack on top: 202 reviews want an alarm rather than a
notification, 119 want control of sound and volume, 59 want snooze.

The opposite pattern is just as clear. "Tasks: To Do List & Reminders" is
visually unremarkable, has no design press, and holds 5 million installs at a
4.78 rating, the highest in the sample. It wins on one axis. It reminds you.

Meanwhile Memorigi, the beautiful one, has the worst rating in the category
(4.33) and the highest one-star share (10%). This review is the whole thesis:

> "I bought the premium, absolutely loved the app but even though you allow
> the app to run in the background it's not giving me notifications. So you're
> obligated to open the app if you wanna see/get reminders. So I ended my sub."

A user who paid, and loved it, left because the notifications did not arrive.

**The three layers this commits us to.** Most apps stop after the first.

1. The API layer. `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`, Doze-aware
   scheduling, a boot receiver, alarm-grade notification channels, full-screen
   intents where warranted.
2. The OEM layer. Samsung sleeping apps, Xiaomi autostart, OnePlus sleep
   standby. Four to seven layers of battery restriction beyond stock Android,
   none of which `isIgnoringBatteryOptimizations()` detects.
3. The trust layer. Detecting that reminders are being silenced, saying so in
   plain language, and letting the user verify a fix. No competitor does this.

Layer 3 is the differentiator. Layers 1 and 2 are table stakes that nobody
has actually met.

**Policy note.** Google Play permits `USE_EXACT_ALARM` for apps whose core
user-facing function is alarms, timers or reminders. Focuslist qualifies
because of this decision. An app where reminders were a side feature would
not.

**What would reverse this.** Nothing short of the reminder problem being
solved by the platform.

---

## D-006. Local-first, no account, no cloud sync

**Decision.** All data is on the device. No account, no server, no sync.
Backup and restore is a file the user controls.

**Why.** Three reasons that happen to agree. Sync failure is 4% to 19% of
low-star reviews across the study. Forced account creation is its own
complaint cluster. And a product with no backend has no running cost, which
is what makes D-001 sustainable rather than a phase.

**What would reverse this.** Sustained demand for multi-device use, weighed
against the fact that adding sync means adding an account, a server, a cost,
and a new top-five complaint category.

---

## D-007. Design is the finish, not the pitch

**Decision.** Keep the calm, Material 3 Expressive, Android-native design
direction. Do not position the product on it, and do not sequence design work
ahead of reliability work.

**Why.** Among 1,500 five-star reviews, "simple and easy" appears in 28.9% and
"the reminders work" in 20.1%. "Beautiful, well designed" appears in 10.0%,
fifth, behind being free.

Design is why someone stays and why they show the app to a friend. It is not
why they install it or why they rate it five stars. Memorigi is the evidence
that beauty without reliability does not hold users.

Note also that the review record does not support minimalism as a pain
reliever. Complaints that an app is too limited outnumber complaints that it
is too complex by roughly three to one, and reviews mentioning overwhelm,
anxiety or guilt number 17 out of 6,051, 0.3%. Calm is the right feel for
this app. It is not the problem the app solves.

**What would reverse this.** Nothing. This is a sequencing rule, and it
expires naturally once reliability is done.

---

## D-008. Follow Material 3 Expressive as given, and stop restating it

**Decision.** The theme supplies one thing: colour. The type ramp, the corner
scale and the motion scheme come from `MaterialExpressiveTheme` untouched.
`ui/theme/Type.kt` and `ui/theme/Shape.kt` are deleted, and the rules written
in their KDoc are superseded by this entry.

Emphasis is a type role. Reach for `titleLargeEmphasized`; never copy a plain
role with a heavier weight.

`MaterialShapes` polygons and shape morphing are allowed, on a budget: one
polygon, in Focus.

**Why.** Principle 5 in `PRODUCT.md` asks for an app that feels Android-native.
A corner ladder is the most legible fingerprint of an app that is not. Nobody
names it, but every card drawn at 16dp where the system draws 12dp is what
makes software read as a skin over Android rather than a part of it.

The deleted scale shifted every step up by one: 4 to 8, 8 to 12, 12 to 16, 16
to 24, 20 to 28. Its stated reason was that this felt softer. Expressive had
already increased rounding over Material 3, so the app was softening a scale
that had been softened for it, on taste alone.

The type overrides were worse, because they fought the spec rather than
merely diverging from it. They put SemiBold on the plain display, headline and
title roles. Expressive's answer to "this should read heavier" is the
emphasized role, and Material derives each emphasized style one step above its
own baseline, Regular to Medium. Setting the plain role to SemiBold therefore
left the emphasized variant a step *lighter* than the plain one. The design
uses emphasized for every heading and the plain roles nowhere, so the app's own
weight decision was being bypassed on every screen while inverting the scale it
was trying to protect.

The polygon ban contradicted the direction outright. `MaterialShapes` and shape
morphing are headline Expressive features; forbidding them while claiming to
follow Expressive is incoherent. The Focus orb is the strongest Expressive
moment in the design, and it was drawn against a rule that forbade it.

**What survives.** Shape does not carry hierarchy. It says what a thing is, not
how important it is, so two components at different levels of prominence do not
get different radii to make the point. Rounded rectangles for list and card
chrome. The polygon is reserved for Focus, where the shape is the content
rather than decoration on content.

**What would reverse this.** Evidence that a specific Material default harms
legibility or reliability on real devices. Taste is not enough. Taste is what
produced the overrides in the first place.

---

## D-009. Phase 2 checks whether the device keeps alarms, not whether it granted permission

**Decision.** The reminder health work in Phase 2 has to answer "is this
device actually delivering exact alarms" rather than "is this app allowed to
ask for them". Those are different questions, and only the first one predicts
whether a reminder arrives.

Concretely, that means the app measures its own delivery: it records the time
each alarm was scheduled for and the time it actually fired, and it inspects
what it got rather than trusting what it asked for. A health screen built on
permission checks alone would report green on a device that silently drops
reminders.

**Why.** This started as an argument from reviews. It is now an argument from
a measurement.

On a OnePlus 8T, Android 14, on 5 September 2026, with the app holding
`USE_EXACT_ALARM` (auto-granted, no prompt) and `canScheduleExactAlarms()`
returning true, `setExactAndAllowWhileIdle` produced this:

    origWhen=2026-09-05 15:00:00.000  window=+8m51s  flags=0x4

Scheduled at 14:48:09, so a futurity of 711 seconds and a window of 531,
which is 0.747 of it. That ratio is AOSP's `maxTriggerTime` heuristic for an
**inexact** alarm, and `flags=0x4` carries no `FLAG_STANDALONE`, which an
exact alarm sets. The app's own fallback warning did not fire, so the exact
API was the one called. TickTick, on the same device at the same moment, had
`window=0`.

Three things were ruled out. It is not our code: the fallback branch logs, and
it stayed silent while the warning string was verified present in the
installed APK. It is not the permission: it was granted and the system's own
check agreed. It is not battery optimisation: adding the app to the deviceidle
allowlist changed the flag from `0x4` to `0x8` and left the window untouched.

The same device freezes the process around every broadcast, logged by
`OplusHansManager` as freeze and unfreeze roughly three seconds apart, which
is the budget a reconciliation gets.

This also explains the exact-alarm spike, which found exact and inexact alarms
arriving within 0.1 seconds of each other across four scenarios. That looked
like a measurement problem. It was the finding: they were the same kind of
alarm.

The cost is no longer hypothetical. On the same device that evening, a
reminder set through the app's own Set Reminder page for 18:25:00 was
scheduled with `exactAllowReason=policy_permission` and `window=+2m19s`, and
the notification arrived at about 18:25:50. Roughly a minute late, with the
screen on, the phone unlocked and in the user's hand, and no Doze involved.
This is the ordinary case, not a stress test.

**What this does not mean.** Not that exact scheduling should be abandoned. It
is free, it is correct on devices that honour it, and asking for it is what
makes the difference visible. See `AGENTS.md`, which keeps
`setExactAndAllowWhileIdle` as the rule and adds the warning that its success
cannot be assumed.

**What would reverse this.** Evidence that the demotion is something the app
causes and can stop, on more than one device. One phone is one phone, and the
honest next step is measuring a second manufacturer before building detection
around a single observation.

**A second measurement, overnight, 6 September 2026.** The 8-hour scenario,
which is the one the first spike never ran: set at 23:53:55 for 07:53:55, an
exact and an inexact alarm side by side, phone left alone off charge, app in
the `active` standby bucket at both ends.

    SET    8 hours exact    exact    in 480min  bucket=active
    SET    8 hours inexact  inexact  in 480min  bucket=active
    FIRED  8 hours exact    exact    delivery -37.7s  clock steady
    FIRED  8 hours inexact  inexact  delivery -37.6s  clock steady

Two things in that.

**Exact bought nothing again, across a whole night.** The two alarms arrived
0.1 seconds apart. The first spike found the same thing over minutes and it
was read as a measurement problem; it survives eight hours and a full Doze,
so it is not one.

**Both arrived 37.7 seconds early**, and `clock steady` means the wall clock
and `elapsedRealtime` agree on that, so it is not the NITZ correction the
two-clock measurement exists to rule out. Early is a different failure from
the window expansion this decision was written about, which makes alarms late.
The likeliest explanation is that OxygenOS delivered the alarm on a wake-up
the device was already performing rather than scheduling its own 37 seconds
later, but that is a guess and nothing here proves it.

**This is not the second manufacturer this decision asks for.** It is the same
phone under harder conditions, so it sharpens the finding rather than settling
it.

It is also, read plainly, better news than the first measurement. Roughly 38
seconds either side of the mark, across eight hours of a sleeping phone, is a
reminder that arrives when it should for anything a person schedules by hand.
What the app cannot promise on this hardware is the second, or even reliably
the right side of the minute. A task reminder survives that. An alarm clock
would not, which is one more reason `PRODUCT.md` does not claim to be one.

**The second manufacturer, 6 September 2026. The demotion is not universal.**

A borrowed Galaxy S24 Ultra, SM-S928B, One UI on Android 16. The same debug
build, the same `setExactAndAllowWhileIdle`, the same permission path. A
reminder set through the app at 13:04:15 for 16:00:00, a futurity of 10,545
seconds:

    type=RTC_WAKEUP origWhen=2026-09-06 16:00:00.000 window=0
    exactAllowReason=policy_permission flags=0x5
    whenElapsed=+2h55m6s756ms maxWhenElapsed=+2h55m6s756ms

**`window=0`.** `whenElapsed` and `maxWhenElapsed` are the same instant, so
there is no slack at all. And `flags=0x5` is `FLAG_STANDALONE` plus
`FLAG_ALLOW_WHILE_IDLE`, where the OnePlus gave `0x4`: the standalone bit that
an exact alarm sets and an inexact one does not, missing there and present
here.

Had this phone demoted the alarm the way the OnePlus does, the window would
have been about 7,908 seconds, 0.75 of the futurity, and `maxWhenElapsed`
would have sat two hours beyond `whenElapsed`.

The same dump carries its own control. Google Maps had an inexact alarm
pending:

    type=ELAPSED origWhen=+3h11m32s363ms window=+4h30m0s0ms flags=0x0

So this device is not simply reporting zero for everything. It distinguishes
exact from inexact, and it put our alarm on the exact side.

**What this changes.** The claim that exact and inexact "were the same kind of
alarm" is true of the OnePlus and false as a general statement. Asking for an
exact alarm buys nothing on OxygenOS and buys everything on One UI. Any
sentence in this document that reads as though the demotion were how Android
behaves should be read as how *that phone* behaves.

**What this does not change: the decision itself, which is strengthened.**
Both phones reported `exactAllowReason=policy_permission`. Both said "Exact
alarms: Allowed". The permission checks were identical and the behaviour was
opposite. An app cannot ask which kind of device it is running on, so measuring
what was actually delivered remains the only honest answer, and a health screen
built on permissions alone would still report green on the OnePlus.

If anything the case is now sharper. It is not that Android is unreliable. It
is that two flagship phones, given the same call, do different things, and the
app has no way to know which one it is on except by watching.

**Still open.** Whether the OnePlus demotion is OxygenOS-wide or particular to
that handset and version, and whether Xiaomi behaves like either. Two
manufacturers is two, and the honest reading of two disagreeing measurements is
that the population varies, not that Samsung is the norm.

**And it delivers.** A second reminder on the same phone, set for 13:30:00
about seven minutes ahead, screen on and unlocked, which is the condition the
OnePlus was measured in:

    dueAt 2026-09-06T13:30   scheduledAheadMs 435690   outcome Announced
    wall-clock error +20ms   monotonic error +19ms   clock drift +1ms

**Nineteen milliseconds.** Against roughly fifty seconds on the OnePlus, awake
and in the user's hand, aimed at 18:25:00 and arriving about 18:25:50. Same
conditions, same code, the same call, a difference of about three orders of
magnitude.

So on both phones the scheduled window predicted the delivery. `window=0` gave
19ms; a window of 0.75 of the futurity gave fifty seconds. That is a tidier
result than this decision assumed, and it is worth being careful about what it
does and does not license.

It does not license reading the window instead of measuring delivery. There is
no public API to read back a scheduled alarm's window; the numbers above came
from `dumpsys`, which an app cannot run on itself. The app still cannot ask
what kind of device it is on. It can only watch what happens, which is what
this decision says.

What it does mean is that the two measurements are consistent with one story:
some skins demote the alarm at schedule time and then honour the demoted
promise. That is a better-behaved failure than a device that promises exactness
and misses anyway, and it is the story to try to break on a third
manufacturer.

**The third manufacturer, 6 September 2026. The OnePlus is the outlier.**

A Xiaomi `peux`, HyperOS V816 on Android 13. `USE_EXACT_ALARM` auto-granted at
API 33, app in the `active` bucket, screen on and unlocked. A reminder set for
14:25:00, about eight minutes ahead:

    type=RTC_WAKEUP origWhen=2026-09-06 14:25:00.000 window=0
    exactAllowReason=policy_permission flags=0x5
    whenElapsed=+7m55s593ms maxWhenElapsed=+7m55s593ms

    dueAt 2026-09-06T14:25  scheduledAheadMs 478790  outcome Announced
    wall error +279ms  monotonic error +280ms  clock drift -1ms

`window=0`, the standalone flag set, and delivery 280 milliseconds late. Three
devices now:

| device | skin | OS | scheduled window | delivery error |
| --- | --- | --- | --- | --- |
| OnePlus 8T | OxygenOS 14 | Android 14 | 0.75 × futurity | ~50,000 ms |
| Galaxy S24 Ultra | One UI | Android 16 | 0 | 19 ms |
| Xiaomi peux | HyperOS V816 | Android 13 | 0 | 280 ms |

Two of three honour the exact alarm. The one that does not is the phone this
decision was written on, and it is worth saying plainly: **the demotion is a
minority behaviour, not how Android works.** MIUI has the worst reputation of
the three skins and it behaved correctly.

**On all three, the scheduled window predicted the delivery.** A zero window
gave tens or hundreds of milliseconds; a window of 0.75 of the futurity gave
fifty seconds. That is a consistent story across three vendors and three
Android versions, and it is the story to try to break next.

**None of this weakens the decision, and one observation strengthens it
sharply.** All three phones reported `exactAllowReason=policy_permission` and
all three said "Exact alarms: Allowed" on the health screen. The permission
answer was identical and the behaviour differed by three orders of magnitude.
An app has no API to read a scheduled alarm's window back, so it cannot tell
these devices apart except by watching what arrives.

**And the Xiaomi showed what the health screen is for.** Its Background
autostart screen listed six apps allowed to start in the background. Focuslist
was not among them, so on that phone the app could not have rebuilt its alarms
after a restart at all. The warning the health screen shows on a Xiaomi is not
a precaution about what MIUI might do. It was, on this handset, a correct
statement about what MIUI had already done.

**Still not measured anywhere but the OnePlus:** delivery across a real
overnight Doze. The Samsung and the Xiaomi were both borrowed and measured
awake. What they establish is that the promise differs by vendor, not what
happens to any of them after eight hours of sleep.

---

## D-010. The deep link into manufacturer battery settings is best effort, and OxygenOS 12 and later is not part of it

**Date.** 5 September 2026.

**Decision.** The reminder health screen's third button opens the
manufacturer's own battery or autostart screen where the phone permits it, and
the app's ordinary Android settings page everywhere else. The routing table
holds no entry for ColorOS or OxygenOS 12 and later, because on those skins no
third-party app can open those screens at all. The button is named after
wherever it will actually arrive, so it never promises Autostart and delivers
App info.

**Why.** Two measurements on a OnePlus 8T, OxygenOS 14, Android 14.

First, the names in circulation are stale. `com.coloros.safecenter`,
`com.oppo.safe` and `com.oneplus.security` are the packages every published
list names for this vendor, and not one of them exists on the device. ColorOS
and OxygenOS merged at ColorOS 12 and renamed everything to `com.oplus`. A
table built from those lists would have fallen through to the generic page on
every modern OnePlus, silently, which is indistinguishable from never having
built the feature.

Second, the replacements cannot be opened. `com.oplus.battery` ships
`PowerAppsBgSetting`, a startup manager and a battery page. All three are
`exported=true`, all three resolve from inside the app, and all three throw on
launch:

    SecurityException: Permission Denial: starting Intent
    { act=com.oplus.powermanager.fuelgaue.PowerAppsBgSetting
      pkg=com.oplus.battery ... }
    requires oplus.permission.OPLUS_COMPONENT_SAFE

That permission is `protectionLevel:signature`, held only by apps signed with
the vendor's platform key. This is not a gap to route around. It is the answer.

So the entries were removed rather than kept behind the guard. Keeping them
would mean three certain failures on every press, and a button that reads
"Open Sleep standby settings" and lands somewhere else.

**What this costs.** On a modern OnePlus the button opens App info, which
carries a Battery usage entry one tap from the setting that matters. Worse
than a deep link, better than nothing, and honest about which it is.

**MIUI is verified too, and it works.** On a Xiaomi `peux` running HyperOS
V816, `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`
resolves and launches, landing on MIUI's Background autostart list. Both MIUI
candidates resolve on that device.

**One UI is verified, and it works.** On a Galaxy S24 Ultra running Android
16, the first Samsung candidate,
`com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity`,
resolves and launches, landing on Samsung's own Battery screen. The app logged
the component it chose. So the deep link is not a dead feature: OxygenOS
refuses it and One UI allows it, which is the same shape as everything else in
D-009.

The other two Samsung candidates do not resolve on that device, and
`com.samsung.android.sm` does not exist as a package at all. They are kept for
older One UI versions, and they cost nothing when wrong.

**What this does not mean.** Not that everything is verified. EMUI is still a
guess, from the same kind of list that proved stale for OxygenOS, and no
Huawei has been tried. The four ColorOS-era names are kept for handsets that
never took the ColorOS 12 update, and were confirmed absent on the OnePlus 8T.

Three of the four vendor families have now been tried on real hardware. Two
work, one is impossible. That is a better record than "best effort" suggested
when this was written, and the entry above should be read with it. They are kept
because they cost nothing when wrong: resolution is checked before launch, the
launch is guarded, and the fallback is the same page. The instrumented test
`aRestrictedDeviceHasAtLeastOneScreenToOffer` is what will report the next one
to go stale, on the day someone runs it on such a phone.

**What would reverse this.** A vendor exposing a documented intent for these
settings, or Android adding one. `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` is
precedent that the platform will sometimes standardise a setting once enough
apps need it.
