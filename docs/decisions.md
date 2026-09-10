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

## D-001. Catimo is free, with no purchases of any kind

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

Memorigi is the closest existing app to the original Catimo plan: a
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

**What would reverse this.** Real Catimo users asking for a place to put
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
user-facing function is alarms, timers or reminders. Catimo qualifies
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
autostart screen listed six apps allowed to start in the background. Catimo
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

---

## D-011. Quick Add reads a time as well as a day, and names the reminder it inferred

**Decision.** Quick Add's field parses a trailing time of day alongside the
trailing day. A day sets the scheduled date. A time sets a reminder.

Three states, and the sheet is still one field and one action:

- **Nothing understood.** The supporting line says where the task will be
  saved, and nothing is marked.
- **A day understood.** The trailing run the parser took is marked in the field
  as it is typed, and the supporting line names the day and the destination.
- **A day and a time understood.** As above, plus one dismissible Reminder
  chip. Dismissing it drops the reminder and unmarks those words in the field,
  so they stay in the title and the text and the outcome cannot disagree.

The day gets no chip. It is corrected by typing, as it always was. No duration
is ever inferred.

**Why a time is read at all.** `date-parsing.md` put times of day deliberately
outside the vocabulary and named "tomorrow at 3pm" as the string to refuse. Its
reason was stated plainly: "A task carries a day and no time, so accepting the
day and dropping the hour would tell the user their 3pm was understood when
nothing about it was stored."

That premise is no longer true, and it stopped being true when Phase 1 shipped.
`PRODUCT.md` defines a reminder as "a promise that the app will interrupt the
user at a specific moment", lists it in V1 scope, and says a reminder is
independent of a scheduled date. A time now has somewhere to live. The rule
outlived the reason it was written for.

**Why the reminder is applied rather than offered.** The alternative was to
detect the time and offer it, requiring a tap before anything is promised. That
feels safer and optimises against the cheaper mistake. `PRODUCT.md` ranks the
two the other way: "A reminder that does not fire is a bug of the highest
severity in this product. Higher than a crash: a crash is visible, a missed
reminder is not."

A reminder set when it was not wanted costs one interruption, and it is visible
and fixable in seconds. A reminder not set when it was expected costs the thing
being missed, silently. The core promise is "if you write it down here, you
will be told", so the default has to be to be told.

**Why only the reminder gets a control.** A wrong day is quiet and cheap: the
task turns up on the wrong day and gets moved. A wrong reminder is a broken
promise in either direction. Giving both a dismiss control would spend
interface on the low-stakes half and imply the two are the same kind of thing.

**What this does not reopen.** `expressive-components.md` declined a row of
Today / Tomorrow / No date chips on Quick Add, because "they would be a second
way to set a date alongside the one the field already has, and the two would
need a precedence rule the user cannot see". That objection is kept, and it is
exactly why the day still has no chip.

The Reminder chip is not a second way to set anything. Nothing else in the
sheet sets a reminder, and dismissing it edits the same marked run the field
already owns, so there is one mechanism rather than two competing ones. The
earlier design failed this test: it left the field reading "tomorrow at 6"
while the task saved without a reminder.

**What was removed getting here.** The board proposed a 30 minute duration
beside the day and the time. It is gone. It is not in the typed text, so it was
never parse feedback; `PRODUCT.md` scopes natural-language *date* parsing; and
a silent default estimate would erase the no-estimate state that `focus.md`
designs for deliberately, where a session counts up, is never offered
plus-five, and carries its own shape.

**What is unchanged.** Only a trailing run is considered, so "Ship the Monday
report" is untouched. Nothing is guessed: what does not parse stays in the
title. The supporting line always carries the outcome in text, because colour
cannot be announced to a screen reader and a rewrite the user cannot see is one
they cannot correct.

**What would reverse this.** Users reporting reminders they did not ask for.
The failure to watch is someone noting when they intend to do a thing and being
interrupted for it. If that erodes trust in the notification itself, then
offering rather than applying is the fallback, and it is a small change: the
chip already exists and would simply start unselected.

---

## D-012. Today is a Focus now card above four ordered bands

**Decision.** Today opens with a card holding at most one task and saying in a
line why that task. Below it the list splits into four labelled bands, in this
order:

    Overdue          past, and needs a decision
    No time set      today, do it whenever
    Later today      today, it will announce itself
    Completed · N    a disclosure, collapsed by default

The card appears only when a task is actionable now, and only for one of three
stated reasons: a paused focus session waiting to be resumed, a reminder time
that has arrived and passed, or a task scheduled for today carrying no time.
Where several qualify, the reason decides first, then the earliest time, then
the order Today already shows. The promoted task leaves its band, so it is
never on screen twice. When nothing qualifies the card is absent and the list
begins at its first band.

**Why the card.** `PRODUCT.md` opens Today with "What should I do now?" and
asks that Today make the answer obvious within seconds. An unlabelled run of
rows does not answer it; it leaves the user to work it out. The card is the
first thing in this app that answers the question the screen exists for.

**This is not the Focus queue D-004 removed.** The queue was a ranking with
nothing to say why its head was its head, and completing the head advanced to
the next one. This card states its reason on screen, and completing its task
re-runs the rule rather than advancing: usually the card then disappears. It
cannot chain, because there is nothing to chain through. Focus still works on
one task and there is still no queue.

**Why "No time set" and not "Anytime today".** D-002 cut Anytime, and its
reasoning was that Inbox / Today / Upcoming / Anytime / Someday is GTD
vocabulary that a broad Android audience does not arrive trained in. The word
has since been removed from the routes, the storage enums and the row menu.
Reintroducing it as a band label, for a different meaning, invites exactly the
confusion D-002 was written to prevent. "No time set" is plain English, says
what the band contains, and is already the app's own wording for a task
without a time.

Rows inside that band do not repeat it. A row reading "No time set" under a
band reading "No time set" is the label twice.

**Why Overdue is first.** The bands are a timeline, and past, present, future
is the order a timeline reads in. More than that: an overdue task is the one
thing on this screen that represents the product's defining failure, a reminder
that fired and was not acted on. Placing it below work scheduled for later in
the day says the opposite of what `PRODUCT.md` says about reliability. It also
needs a decision — do it, move it, drop it — and a band placed last is a band
that rots.

The counter-argument, that opening on overdue work is a guilt list, is real and
was weighed. The card sits above all of it, so the screen still opens on what
to do rather than on what was missed.

**What this supersedes.** `docs/design/today-screen.md` § Sections describes
three bands, the first unlabelled because "today's work needs no announcement",
and a Completed section that is "not collapsible" and carries no count. All
three change here. The first band is now labelled because it is no longer
first, so the argument for leaving it bare retires with the position. Completed
becomes a collapsed disclosure with a count because a plain completed list
grows all day and pushes live work down the screen; the count keeps the day's
progress visible without spending rows on it.

**The card opens its task, and for a while it did not.** `FocusNowCard` carried
a comment saying "the card is not itself a button ... a clickable container
behind two controls is a third target the user cannot see the edges of". That is
a fair worry about overlapping targets and it answers the wrong question.

This entry takes the promoted task out of the bands below, so it is on Today
exactly once. A card that took no click therefore left that one task's details
unreachable from the screen entirely: not in a band, not in Upcoming, which
holds later days, not in Inbox, which holds undated work. The task the screen is
built around was the only one on it that could not be opened without first
completing or rescheduling it. A rule that removes a row has to account for what
the row could do.

The overlapping-target worry is answered by the arrangement every task row
already uses: the children take their own clicks and the body takes the rest.
The card is that, with a button on the end. It carries the same
`Open task details` action label the rows carry, because it is the same act.

**A dependency worth naming.** The card's first and strongest reason is
"resume paused focus", and that presumes Focus has a session that can be paused
and that survives leaving the sheet. Focus has neither. `focus.md` describes two
states, Ready and a running Session, and its Out of scope list names "a pause, a
resume, or a session history". D-004 froze Focus on top of that. D-013 changes
both. Until it is built, two of the card's three reasons stand on their own and
the first cannot fire.

**What would reverse this.** Users reporting that the card picks the wrong
task often enough that they stop trusting it. The failure to watch for is the
card feeling arbitrary despite the reason line, which would mean the reason is
not doing the work it was put there to do.

---

## D-013. Focus gains pause and resume, superseding D-004's freeze

**Decision.** Focus stops being frozen. It gains a pause, a resume, an
extension when the estimate is reached, and the paused counterpart of both the
timed and the open-ended session. Six states:

    Ready              45:00   45 min focus               Start focus · Complete
    Running            44:37   45 min focus               Pause · Complete
    Paused             32:18   Paused · 32 min remaining  Resume · Complete
    Estimate reached   00:00   Estimate reached           Complete · +5 min
    Open-ended         12:43   Elapsed time · No limit    Pause · Complete
    Open-ended paused  12:43   Paused · No time limit     Resume · Complete

A paused session survives leaving the Focus sheet. A running one still does not.

At the estimate, Complete is the primary and +5 min the secondary. A timer
running out is more often the moment work is finished than the moment it needs
extending, and the control that reads as the default should be the likelier one.

**Why now, and what is not met.** D-004 named its own reversal conditions: "1.0
shipping, reminders being genuinely reliable, and a wish to keep building."
Reminders are measured rather than hoped for, across three manufacturers, and
D-009 stands. **1.0 has not shipped.** One of the three conditions is unmet, and
this entry is being written rather than the change being quietly made for
exactly that reason.

The argument that carries it is D-012. The Focus now card's first and strongest
reason is "resume paused focus", and there is currently nothing to resume.
Building the card without this ships two of its three reasons and leaves the
most useful one as a comment.

D-004's real content was sequencing, not that Focus was wrong: "further
investment in it is investment not going into reminders." That argument is
spent. Phases 1 and 2 are complete, the reliability work is measured, and the
thing being built now is Today.

**This also supersedes `focus.md`'s Out of scope list**, which names "a pause, a
resume, or a session history". The pause and the resume come back. Session
history does not, and nothing here asks for it: no log of past sessions, no
totals, no streaks. `PRODUCT.md` principle 7 rules those out, and the Logbook
already records completed work.

**A paused session outliving the sheet is the part to be careful about.** The
existing rule is that a session running with nothing on screen pointing at it is
state the user can neither see nor reach, which is why leaving stops it. That
reasoning survives for a running session and does not survive for a paused one:
the Focus now card is the thing pointing at it, in the user's line of sight, on
the screen the app opens to. The card is what makes leaving a paused session
safe, which is why the two arrive together rather than one at a time.

**What this does not reopen.** Not the queue, which D-004 also removed and D-012
declines to rebuild. Not Focus as a navigation destination; it stays a sheet
over the screen that asked for it. Not the store listing, which still does not
lead with Focus.

**What would reverse this.** Time spent here showing up as reminder work not
done. The check is `ROADMAP.md`: if Phase 4 slips while Focus grows, this was
the wrong call and D-004 was right about sequencing after all.

---

## D-014. The Focus shape says whether the session is running, not how far it has run

**Decision.** The session shape is `Cookie4Sided` at rest and `Cookie12Sided`
while running. It morphs between them on state change, on `focusSession`, and
does not move in between. Pause returns it to 4-sided; resume takes it back to
12. Open-ended sessions follow the same rule, because it makes no reference to
an estimate.

The remaining time stays on screen as digits, at Headline Small rather than
Display Large. The task title leaves the shape and becomes a heading above it,
capped at four lines. The shape holds the digits alone.

    Ready              4-sided    45:00    45 min focus
    Running           12-sided    44:37    45 min focus
    Paused             4-sided    32:18    32 min left
    Estimate reached  12-sided    00:00    Estimate reached
    Open-ended        12-sided    12:43    No time limit
    Open-ended paused  4-sided    12:43    No time limit

**What this supersedes.** `focus.md`'s entire morph design: the determinate walk
from `Circle` to `Clover8Leaf` against the estimate, the six-shape ring for
sessions without one, the twenty-minute cycle, and the rule that the shape must
be unreadable as a gauge. Also `expressive-motion.md`'s statement that the app's
one permitted shape morph is the one carrying progress. The morph survives; what
it says changes.

**Why.** D-013 put a readable value on this screen and did not say what that
left the shape doing. Two things then measured the same quantity, and the shape
was the worse of the two at it: it cannot be read to a number, it publishes
nothing to a screen reader, and the board had already frozen it at full
extension in all six states without recording that. A second progress channel
that is strictly worse than the first is not a second channel, it is decoration,
and `expressive-motion.md` bans decoration.

Saying whether the clock is moving is a different job, and one the shape is
good at. It is the question a glance at this screen asks, the digits answer it
only by being watched for a second, and principle 7 rules out anything that
invites that watching.

**Why the number shrinks and the title leaves the shape.** Display Large made
the countdown the largest object on a screen whose stated purpose is to stop
clock-watching, and left the task title smaller than the number. Principle 2
puts the work first.

The title moves out because it does not fit inside. A cookie yields about 70% of
its box as usable area, so four lines at 200% font scale needs a 514dp square on
a 412dp screen, and three lines needs 411dp with nothing left for margins.
Anything inside the shape is capped at two lines, and the 240dp column there
fills at 24 characters. Outside it the title has the full 380dp width and the
four-line cap holds at every font scale.

This costs `focus.md`'s line that the title is "the fixed thing the session
forms around". That argument was built for the container transform, where play
grew into a shape around stationary words. The board draws no container
transform, Ready already shows the shape, and the morph design it belonged to is
the one being replaced here.

**What this costs.** The app's shape work stops being a progress indicator,
which was the more distinctive idea. It is also, on the board's own evidence,
the one that was never drawn.

**What would reverse this.** Users unable to tell a paused session from a
running one at a glance. The check is whether the shape reads as a state at all,
which needs watching on a device.

---

## D-015. Leaving the Focus sheet pauses the session rather than stopping it

**Decision.** The close control and the back gesture both pause a running
session. Neither stops it. Nothing is discarded by leaving, in any state.

**What this supersedes.** D-013's clause that "a paused session survives leaving
the Focus sheet. A running one still does not." Also `focus.md`'s hand-written
back handler, which existed only to stop the session on back.

**Why.** The control cannot tell which of two intentions a tap carries. "I am
finished with this" and "I need to look at something else for a minute" are both
ordinary, and they arrive through the same button. So the question is not which
one is more likely, it is which mistake is cheaper to be wrong about.

Stopping when the user meant to pause loses the elapsed time and the sense of
progress with it, silently, with nothing that puts it back. Pausing when the
user meant to stop leaves one card on Today that they can ignore, and that
completing the task clears. One failure is unrecoverable and invisible; the
other is visible and costs a glance. This is the same ranking `CLAUDE.md` makes
when it puts a missed reminder above a crash: the silent loss is the worse one.

**Why this is not D-013 reversed.** D-013's reasoning was that a session running
with nothing on screen pointing at it is state the user can neither see nor
reach, which is what made leaving unsafe. Pausing on close means that situation
never occurs. The principle is unchanged; only the conclusion moves, because the
principle now points the other way.

**What it costs.** There is no longer a control that stops a session outright.
Stopping was only ever pausing and not resuming, and the task itself is still
one tap from Complete, so nothing is unreachable. A user who abandons a session
leaves a paused one in the Focus now card until they finish the task or start
another. That is clutter, and it is the price.

**What would reverse this.** Paused sessions accumulating in the Focus now card
and reading as nagging rather than as a way back in. The check is whether anyone
resumes them.

---

## D-016. The Logbook groups by day

**Decision.** The Logbook keeps its day headings. Completed tasks are grouped
under the day they were finished, newest day first, and the ordering inside a
group stays `completedAt` descending.

**What this supersedes.** `logbook.md`'s Out of scope list, which names
"grouping by day, week, or month".

**Why.** The Logbook is ordered by completion time and nothing on the screen
says so. A reader cannot tell whether the twelfth row is from yesterday or from
March, which makes the one ordering decision this screen makes invisible. Day
headings are the cheapest possible fix: they add no data, because `completedAt`
is already the sort key, and no new field, query or state.

**Why the original entry did not mean this.** The out-of-scope list is about
keeping a scoreboard out, and the document says so in its own words: the last
two items matter most, `PRODUCT.md` rules out streaks and productivity scores,
"and a list of finished work is exactly where those would try to creep in. The
Logbook is a record, not a scoreboard." A date divider is not a scoreboard. It
counts nothing, compares nothing across days, and says nothing about how much
was done. Grouping was swept into that list alongside the statistics it was
written to exclude.

**Where the line still is.** No count beside a day heading, which is the obvious
next step and is the step that turns a record into a scoreboard. No week or
month grouping, no calendar, no "you finished 12 tasks this week". A heading
says when, and only when.

**What would reverse this.** A count appearing next to a day, or the headings
being used as an argument for a summary above them. That is the failure mode,
and it is the reason the previous entry existed.

---

## D-017. Today's header carries the date and nothing else

**Decision.** The pill carrying the total time still planned is removed from
Today's header. The subtitle is the date, alone, and the date is written in
full: "Thursday, October 24" rather than "Thu, Oct 24".

`todayPlannedMinutes` goes with it. The pill was its only caller.

**Why this entry exists at all.** The pill was removed from the design some time
ago and appears on no board frame. `today-screen.md` recorded the removal and
then said, twice, that it had no entry here and should have one "before anything
else is built on top of it". Today's four bands and the Focus now card were then
built on top of it. This is that entry, written late.

**Why the pill goes.** It is a number nobody asked for, on the screen
`PRODUCT.md` says should make one answer obvious within seconds. "2h planned"
does not help anyone choose a task: it is the sum of a set of estimates, most
tasks carry none, and the total changes when work is rescheduled rather than
when it is done. The Focus now card now answers "what should I do now" directly,
which is the job the pill was gesturing at and failing to do.

It also brushes against principle 7. A running total of outstanding work, sitting
under the screen's title all day, is the shape of a productivity score even when
nothing is compared to anything. The reward is getting the work done.

**Why the date grows back to its full form.** The abbreviation existed to make
room for the pill. `TodayScreen` said so: "the long form spent most of the line
on two words the user is not reading; the short form says the same thing and
leaves the width to the total at the other end." There is no total at the other
end any more, so the constraint is gone and the board's own form — the full
weekday and the full month — is what the header shows. The year stays out; it is
noise on a screen about today.

**What this does not change.** The bar is still `LargeFlexibleTopAppBar` at its
default 152dp, still collapsing to 64dp as the list moves. `today-screen.md`
argued the tall bar on two things, the date and the pill, and noted that losing
the pill left the case resting on the date alone and "thinner than when the case
was made". It still holds: the date is genuinely unavailable elsewhere on the
screen, and the bar gives its height back the moment anyone scrolls. If the
opening view ever needs protecting, that section already says this is the first
place to look.

**What would reverse this.** Users asking how much is left in their day, often
enough and unprompted. The answer then is probably not a pill in the header,
because that is where it just failed; it is a number somewhere a person goes
looking for it rather than one that follows them around.

---

## D-018. Task Details is a picker-driven screen, and commits as you go

**Decision.** Task Details is a full screen reached from a task row, not a
bottom sheet. The title and the notes are editable in place at the top. The
scheduled date, due date, reminder, duration and repeat are rows that open a
sheet of presets and a picker. There is no typed date entry on this screen, and
there is no Save: every control writes when it is chosen.

**What this supersedes.** `task-details.md` in most of its particulars: the
sheet, the draft held until Save, the date text fields, the parser confirmation
line underneath them, and Save being disabled while a field is unusable. Also
its "Out of scope" line naming recurrence and reminders, which contradicted its
own list of the six fields it edits and contradicted `PRODUCT.md`, which names
recurrence among a task's properties.

**Why.** Capture by typing, edit by picking. D-011 put the parsing effort at
capture, where speed is everything and the user is already at a keyboard. On an
edit screen the user has arrived deliberately to change one thing, and a preset
or a calendar is faster and unambiguous where a sentence has to be parsed and
then confirmed back. It is also the more Android-conventional shape and the more
discoverable one: a row showing `Due date  None` says what can be set, and an
empty text field does not.

**What it costs, and this is not small.** `TaskDetailsSheet.kt` is about 950
working lines built to the superseded design, and it goes. So does a real safety
property: the draft meant dismissing left the task exactly as it was, and
immediate commit has no equivalent. The mitigation is that each write is one
field, made by one deliberate choice, and reversible by reopening the same row.
That is weaker than a draft and it is the price of the model.

**A preset may offer what the parser refuses.** `date-parsing.md` excludes
`next week` from the vocabulary because English does not agree on which week it
means. A button does not have that problem: it carries one defined meaning.
So the two vocabularies are allowed to differ, and the reason is that a button
resolves the ambiguity that made the phrase unparseable.

Two presets need their meaning fixed, because neither is in the parser and
neither is self-evident:

    This weekend    the coming Saturday
    End of week     the coming Friday

`Next week` was on the board and was dropped anyway. It was the one preset the
parser excludes by name, and its cell was needed for the clear option.

**Both date sheets are the same shape:** the clear option first, then three day
presets in a 2x2 grid, then a full-width Choose a date. Scheduled had no clear
option at all, which meant a task could not be returned to the Inbox from here.

**Unset values are not styled differently**, and the question comes up every
time someone reads the screen. `None` and `Doesn't repeat` are values the user
chose or accepted, not gaps. Dimming them would make the screen read as a form
with blanks to fill, which principle 3 contradicts by saying a task needs only a
title, and which principle 4 calls task administration. The only quieter token
available also fails AA at 14sp.

**What would reverse this.** Users losing work to a control they did not mean to
touch. The draft is the thing being given up, so that is where the failure will
show.

---

## D-019. The Repeat editor is designed now and built in Phase 4

**Decision.** The nine Repeat frames on the board are a Phase 4 design. They are
not built against the current `Recurrence`, which stays a four-value enum:
`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`.

**Why this needs saying.** `Recurrence.kt` does not merely lack an interval, a
weekday set, an end date and an occurrence count. It excludes them, in writing:
"the smallest reading of it: four fixed periods, and no interval, no weekday
set, no end date, and no occurrence count ... each of the others is a column and
a control that can be added later without changing what is here."

The board designs all four of them. Read as an instruction to build, that is a
schema change and a rule engine arriving through a Figma frame, which is exactly
what the drift guard exists to catch.

**Designing ahead is allowed; building ahead is not.** `CLAUDE.md` says a task
from a later phase is a task for later. It says nothing against knowing what the
later phase looks like, and the design is more useful drawn than imagined. The
line is that no part of it enters the code until Phase 4 is the current phase.

**What has to be true before it is built.** The four fields become columns, and
`nextOccurrence` becomes a rule evaluator rather than a period step. The KDoc's
claim that each can be added "without changing what is here" should be checked
rather than trusted at that point, because an end date and an occurrence count
both need to know how many occurrences have already happened, and nothing
records that today.

**What would reverse this.** Nothing needs to. When Phase 4 begins this stops
being a deferral and becomes the specification.

---

## D-020. Today's header goes back to the compact bar, and loses the date

**Decision.** Today, Inbox and Upcoming use a compact 64dp top app bar with the
destination name as its title and no subtitle. `LargeFlexibleTopAppBar` goes.
The date comes off Today.

**What this supersedes.** `today-screen.md`'s "Top app bar" section entirely,
including the reversal it records, and D-017's closing clause that the bar "is
still `LargeFlexibleTopAppBar` at its default 152dp".

**Why.** The tall bar was never justified by the title. `today-screen.md` is
explicit that the argument it overturned was "right **about a title**", and
overturned it only because "the header now carries information rather than a
label". D-017 then removed the planned-minutes pill and narrowed the case to the
date alone, calling it "thinner than when the case was made".

Removing the date removes the last of it. What is left is the situation the
original rule described and refused:

> A tall header would spend the most valuable part of the screen restating a
> label the user already knows, and it would push the first tasks down out of
> the opening view. Task visibility wins over header prominence.

That reasoning was never withdrawn. It was suspended while the header held
something the screen did not otherwise say. It does not any more, so it applies
again, and this entry is the suspension ending rather than a new idea.

**What it buys.** 88dp on the opening view, roughly one and a bit task rows, on
the screen `PRODUCT.md` calls the centre of the app and asks to answer one
question within seconds. `today-screen.md` names this route itself: "go back to
the compact bar and find another home for the date."

**Why the title stays the destination name.** Making the date the title was
considered. It puts the size on the information and reads well, but this header
also serves Inbox and Upcoming, whose titles are their names and which have no
date to substitute, so it would use one component two ways. An app bar title
echoing a navigation bar label is also ordinary on Android; what was wrong was
spending 36sp on it, not saying it at all.

**Why the date is not rehomed.** Nothing on Today needs it. The bands are
relative (Overdue, No time set, Later today) and so is the Focus now card. The
date was pleasant rather than load-bearing, and no band, row or card is harder
to read without it.

**The collapsed header component goes with it.** A pinned 64dp bar has nothing
to collapse to. `Focuslist / Screen header — Collapsed` existed for the tall
bar's scrolled state and has no job now.

**What would reverse this.** Users asking what the date is, or misreading a
relative label because they had lost track of the day. The answer then is the
date as Today's title in the compact bar, not the tall bar coming back: the
height was never what made the date useful.

---

## D-021. A risk the app inferred does not look like a refusal it was told about

**Decision.** `CheckState.Warning` gets its own treatment, distinct from
`Blocked`. A device whose only problem is an inferred manufacturer restriction
no longer reads "Action needed" in error colours.

    Missed          a delivery went wrong          error
    Action needed   a check is Blocked             error
    Worth checking  only Warnings, none Blocked    neutral
    Ready           all three Ok                   primary

The check row gains the same three-way split: `Ok`, `Warning`, `Blocked`, where
`Warning` takes the ordinary row surface and `Blocked` keeps the error
container.

**Corrected on the render: neutral, not tertiary.** This entry said tertiary,
and the section below still argues for it. It does not survive contact with the
palette: `tertiaryContainer` is `#FFD7E3` and `errorContainer` is `#FFD8D6`, one
step apart in green, so the caution and the error were indistinguishable on
screen. `reminder-health.md` records the finding and the wider lesson, which is
that this palette has three usable container families rather than five. The
decision below is unchanged in everything but the colour: a guess still gets its
own treatment, and that treatment is the absence of a tint.

The body of a Warning says what the app cannot know:

    Sleep standby can delay reminders.
    Catimo cannot tell whether it is on.

**Why.** `CheckState` already made this distinction and the screen threw it
away. `Badge()` branched on `Ok` against not-`Ok`, so a feature the app is
guessing at rendered exactly like a permission the user had explicitly refused.

That is not a small population. `DeviceRestriction` is inferred from
`Build.MANUFACTURER` and never measured, because none of these features is
visible to any API. So every OnePlus, OPPO, Realme, Xiaomi, Redmi, POCO,
Samsung, Huawei and Honor user saw a permanent red "Action needed" from first
launch, on a phone where nothing might be wrong at all.

**A reliability screen that is always red teaches people to ignore it.** That is
the failure this app can least afford, because `PRODUCT.md` principle 1 puts a
missed reminder above a crash, and the whole value of this screen is that the
user believes it when it finally says something.

**Why not stay silent instead.** Waiting for a real miss before mentioning the
restriction was considered and rejected. It is the more honest position and it
accepts a missed reminder as the price of learning, which principle 1 forbids.
The warning has to be pre-emptive; it just has to be accurate about its own
certainty.

**Why tertiary, and why it did not survive.** The app already uses `tertiary`
for an overdue date, chosen there because the colour is a second cue on top of a
distinction that is already textual. The same reasoning holds here: "May block
background alarms" and "Not allowed" already differ in words, and the colour
only has to stop contradicting them.

What did not hold is that `tertiary` reads as a third colour in this palette. It
does not, against `error`. So the second cue is the glyph — a question mark
rather than an exclamation — and the tint is simply absent, which says the same
thing more plainly: the app colours what it knows.

**What stays loud.** A refused permission is still an error, because the app was
told. A late delivery is still an error, because it happened. The escalation
exists, so a user who ignores a tertiary caution and then misses a reminder gets
the red screen they should.

**What would reverse this.** Users on affected devices ignoring the caution and
missing reminders because of it. The check is whether anyone opens the
restriction settings from that state.

---

## D-022. Task Details can delete, from an overflow rather than a button

**Decision.** Task Details gains an app-bar overflow holding one item: Delete,
labelled, in `error`. The checkbox beside the title stays a checkbox.

**What this supersedes.** The Known gap in `task-details.md`, which recorded the
overflow as drawn on the board and unspecified in code. Also the last of the
superseded document's "completion and deletion are deliberately absent": D-018
brought completion back and left deletion out.

**Why deletion belongs here.** The screen exists for deciding about one task, and
deciding a task is not worth doing is one of the outcomes. Deletion was reachable
only from the row's long-press menu, so the screen that is entirely about a task
was the one place you could not throw it away. That is a detour back to a list to
act on something already in front of you.

**Why not an icon button.** Three reasons, and the second is already written
down.

An unlabelled trash icon is the least legible form of the most destructive
action, and every other Delete in this app is a labelled menu item.

`expressive-components.md` decided the principle for the row menu: "Focus first
where it is offered, then Delete. Constructive before destructive, so the thumb
does not land on Delete." An icon in the app bar puts Delete one tap from Back,
in the corner a thumb reaches for when leaving. That is the same mistake in a
different place.

And the bar's trailing slot is the overflow on Today, Inbox and Upcoming. A
different control there would make one slot mean two things.

**Why not a button beside Start focus.** Same thumb argument, and it would give a
rare one-way action the same weight as the screen's primary. Three actions,
three weights: Start focus is full-width because it is the payoff, completion is
a checkbox, Delete is a menu item because it is rare and terminal.

**Why the checkbox stays a checkbox.** Completion is reversible state, not a
one-way action, and only a checkbox says so. Unticking is how a task is reopened
from the Logbook. A "Mark done" button would have to become "Mark not done" on a
completed task, which is a button impersonating a toggle. It also keeps
completion identical on every row, in the Logbook and here.

**No confirmation dialog.** Deletion is a soft delete raising the same single
undo offer every list raises, and the screen already hosts the snackbar and
already leaves when its task disappears. A dialog would be a second question
after a reversible answer.

**One item in a menu is enough.** It buys one tap of protection without a dialog,
and `navigation.md` already allows it: the app-bar overflow carries destinations
on the three primary screens, and a room may carry its own action menu under the
same glyph.

**What would reverse this.** Accidental deletions. The check is whether the undo
offer is being used from this screen more than from the lists.

---

## D-023. The task row loses its actions menu

**Decision.** The task row is a checkbox, a title, a metadata line and a
duration, and tapping it opens Task Details. The trailing actions button, the
long-press menu, and the `onDelete`, `onFocus` and `onReschedule` callbacks all
go. That is the anatomy the board has always drawn.

**What this supersedes.** `expressive-components.md`'s Task row anatomy and its
Task actions menu section, including the reversal that added the button. Also
`focus.md`'s second way into Focus.

**Why: the reversal's premise was removed by three later decisions.**
`expressive-components.md` justified the button like this:

> Delete and Focus live only in the actions menu, **Task Details deliberately
> excludes both**, and long press was the only way to reach them. `PRODUCT.md`
> says the UI must not depend exclusively on gestures.

D-018 gave Task Details a Start focus. D-022 gave it Delete. Its Plan rows give
it rescheduling. Task Details excludes none of them any more, so the gesture the
button existed to back up no longer carries anything alone. The button outlived
its reason without anyone going back to check.

**The menu had also become a worse duplicate.** It offered Today, Tomorrow and
Pick a date; the Scheduled sheet offers No date, Today, Tomorrow, This weekend
and Choose a date, on a screen that also holds the due date, the reminder, the
duration, the repeat, Start focus and Delete. The menu is a subset that cannot
clear a date.

**Rescheduling is administration, and it was the most visible thing on every
row.** `PRODUCT.md` principle 4 says the app should encourage execution rather
than task administration, and three of the menu's five items were rescheduling.
A permanent trailing button gave that the most prominent position on every line
of every list. `expressive-components.md` recorded the price in width: adding it
"pushed two of five seeded titles onto a second line until the screen margin was
returned to `md`".

**What it costs, plainly.** Rescheduling from a list goes from two taps to
three, and rows lose long press entirely. Nothing becomes unreachable; several
things get one tap further away. That is the trade, and it is being made on
purpose rather than absorbed quietly.

**One simplification falls out.** `focus.md` had three entries, and the row
long-press was the one that started a session directly, "because picking one
task out of a list and choosing Focus on it is the deciding already done". With
it gone, every entry lands on Ready and the user presses play. The special case
disappears and Ready stops being reachable by only some routes.

**What would reverse this.** Rescheduling from a list proving too slow in real
use. The answer then is a bottom sheet on long press, which is what Material's
compact guidance points at for a five-item menu, not this button coming back.

---

## D-024. Settings is four rows, and the list is closed

**Decision.** Settings holds Reminder health, Dynamic color, Theme, and Backup &
restore. Three sections, four rows. The list is closed by default, and adding to
it needs an entry saying what the product declined to decide.

**Why close a list nobody has added to yet.** Settings is where a task app goes
to avoid choosing. Every other screen in this product has a document defending
what it excludes, and this is the one screen where exclusions arrive one at a
time, each individually reasonable, none of them ever removed. The drift guard in
`CLAUDE.md` exists for exactly this shape of change, and it works by having
written the answer down first.

**A setting is a decision the product refused to make.** Applied to the four that
stay, each is about the user's device or the user's data, and none is about how
the app behaves:

- Reminder health reports on the device.
- Dynamic color and Theme are the device's appearance.
- Backup & restore is the user's data.

Nothing in the list changes what a task is, how a list is ordered, or when a
reminder fires. That is the test a fifth row has to pass.

**Four candidates are refused by name**, because each has a home already. A
default reminder time reintroduces the times of day `date-parsing.md` keeps out
of the vocabulary. Week start, date format and time format are Android's, and
principle 5 says embrace the platform rather than re-ask. Sort order, grouping
and density would undo `today-screen.md`'s bands and `task-row.md`'s anatomy.
Notification sound, vibration and importance belong to the Android channel, and
a second copy here would be two switches that can disagree about a reminder,
which principle 1 makes the most expensive kind of bug in this product.

**The theme chooser commits on selection.** A dialog of three radio rows, no
Cancel and no OK. Same rule as D-018's pickers: the choice is instantly visible,
instantly reversible, and a confirm step would only add a tap. A dialog rather
than a destination, because three exclusive options needing no explanation is
what an alert dialog is for, and a fourth room for three radio buttons would
contradict principle 6.

**What this costs.** A user who wants a default reminder time does not get one,
and the answer is that they set the time on the task. A user who wants the app's
sound to differ from the system channel's has to use the system channel. Both
are real refusals rather than gaps waiting to be filled.

**What would reverse this.** A row earning its place is one where the app cannot
choose correctly for everyone and the wrong choice loses a reminder. That is a
high bar on purpose. Aesthetic preference is not it.

**What was fixed on the board while settling this.** The three section headings
were 22sp Medium on `onSurface`, the app bar's own type, carrying no text style
at all, so "Reminders" was as loud as "Settings" over a single row. They are
`M3/title/small` on `onSurfaceVariant` now, matching Today, Upcoming and the
`SectionLabel` the code already shares. Backup's two headings dropped 22 to 16,
giving the page three sizes instead of two. All four modal scrims were wrong in
opposite directions, the three theme frames binding `Scrim` at full opacity and
the Restore Error frame using 30% on an unbound colour; all four are `Scrim` at
Material's 32%. And the Restore Error frame had lost the entire Backup page
behind its dialog, which the opaque scrim was hiding: it is rebuilt.

**What is deliberately not shown.** `Settings — Dynamic color off` differs from
the clean slate by one switch, and both are painted in the app's own `Light`
scheme. Section 18 owns the wallpaper palettes across Today, Focus and the
notification; drawing them again here would mean maintaining that story twice.

**One thing the frames do not show, and it is the expensive part.** Both
Appearance rows need persistence the app does not have. There is no DataStore
and no SharedPreferences in `app/src/main`, and `FocuslistTheme` takes
`darkTheme` and `dynamicColor` as parameters that nothing outside previews
overrides. Phase 5's first bullet is a preferences store, and the screen after
it.

---

## D-025. Text fields are filled, not outlined

**Decision.** Every text field that draws a container draws the filled one.
`OutlinedTextField` is replaced by `TextField` throughout.

**What this supersedes.** `expressive-components.md`'s "`OutlinedTextField`
throughout", which was an implementation note rather than an argued position: it
recorded which component was in use and never said why that one.

**What it does not touch.** Task Details' title and notes stay borderless. They
are already the filled component with its container painted out, which D-018
asks for in as many words: the title is the screen's heading and its primary
input at once, and a container around it makes the top of the screen read as a
summary card. Filled is the default for a field that announces itself as a
field; those two deliberately do not.

So the rule is narrower than "filled everywhere" and worth stating precisely:
**a field that draws a container draws a filled one.** Two fields draw none.

**Why filled.** It is Material's own default and the one the rest of this design
system already agrees with. Every other surface in the app is a tinted container
on a plain page — task rows, Plan rows, the Focus now card, the health check
rows, the headline card — and an outlined field was the one component asking to
be read by its border instead. Filled also gives the field a larger touch
target's worth of visible affordance at no cost in height.

The `[CD]` notes under Inputs are unaffected and all still hold: trailing
controls go in the trailing slot, clear is an icon whose description names its
field, every field carries a label, a constrained format carries a placeholder,
errors use the Material error treatment plus supporting text, and a single-line
placeholder is capped to one line.

**What would reverse this.** A filled container reading as a chip or a button
somewhere it sits beside real ones. The place to watch is Quick Add, where the
field sits above a filled Add task button on the same sheet.

---

## D-026. The Duration sheet is a connected group, and Custom is a state of it

**Decision.** The five duration choices are one `ButtonGroup`, not five `Button`s
in a `Row`. Custom is a second state of the same bottom sheet behind a back
arrow, not an `AlertDialog` raised over it. The Custom row shows the estimate
when no preset holds it.

**What this supersedes.** `expressive-components.md`'s Segmented controls
section, which described `SingleChoiceSegmentedButtonRow` for a recurrence
picker that no longer exists and settled the overflow question on sideways
scrolling. Also its Dialogs section, which claimed `DatePickerDialog` was the
only dialog in the app while there were four.

**Why the group: the row it replaced was one label's growth from breaking a
rule this document already had.** `expressive-components.md` says a row of
choices "must not overflow at large font scales, and must not truncate a label
to avoid doing so", and five equal-weight buttons with `maxLines = 1` and no
overflow behaviour had nothing to do but clip. Whether it clipped at 175% or at
200% was calculated, not observed, and the calculation was wrong in its details:
it ignored that the overflow indicator takes width of its own. The structural
point stands on its own. A control with no answer for running out of room is
one label away from breaking the rule, and the rule was written down here before
the control that broke it was built.

`ButtonGroup` answers it in the component: what does not fit moves into a menu
behind an indicator, so every option stays selectable and none is cut. That is a
third answer alongside the scrolling the earlier section chose, and it is better
for the reason a menu beats a scroll anywhere, the indicator is visible where
off-screen content is not. It is also what the board draws.

**Measured afterwards, the menu never appears.** All five presets stay laid out
inside the sheet's 380dp at every font scale Android offers, 100% through 200%.
So the overflow behaviour is insurance rather than a cost, and the worry that
sent us measuring, that a low-vision user would meet a menu where everyone else
sees buttons, does not happen.

**That was a one-off measurement and nothing guards it.** The test written to
take it was deleted rather than kept, so a sixth preset, a longer label or a
change in Material's spacing would move the number with nothing to say so. Take
the measurement again before trusting it.

**How it was nearly got wrong, because the next person will reach for the same
probe.** Counting labels in the semantics tree reports all five present at every
font scale, and also reports all five present inside 120dp, which nothing that
fits can be true of. Overflowed options do not leave the tree; only their bounds
change. Measure bounds, and squeeze the group into a width nothing could fit to
prove the probe can still fail.

**Why the state rather than the dialog: the old code's own comment argued for
it.** It read:

> A dialog rather than a second sheet, because a modal sheet on Android is a
> dialog with its own window: stacking one on another darkens the scrim twice
> and makes back a question about which of the pair receives it.

That is an argument against a second *window*, and an `AlertDialog` over a
`ModalBottomSheet` is two windows. The board never proposed a second sheet; its
frames are named "SAME ModalBottomSheet" and draw a back arrow. So the design
the comment was rejecting is the one that satisfies it. Back is now one
`BackHandler` on one sheet.

It also drops a Done/Cancel confirm from the screen D-018 made commit-as-you-go.
Done writes and closes, like every preset beside it. Nothing is written until
Done, so leaving by the arrow or the scrim needs no Cancel.

**Why the value on the Custom row.** The sheet could not show a custom estimate
at all. Set 1h 20m and it lit no preset and said nothing, so the one value the
presets cannot express was the one value invisible. The board solved this with a
48sp readout above the presets; this puts it on the Custom row instead, as
`Custom  1h 20m`, which is the `PlanRow` pattern the rest of the screen already
uses and does not restate at display size a number the user set one gesture ago.

The value appears only when no preset holds it. `Custom  45m` one line under a
lit `45m` button would say the value was typed when it was pressed.

**What was wrong in the file's own documentation.** The `DurationSheet` KDoc
said "The value shown at the top is the task's current estimate, which is what
tells the user whether they are changing something or setting it for the first
time." No such readout was ever built. The comment described the board frame
rather than the code beneath it, which is the failure mode this project's
commenting style is most exposed to.

**What would reverse this.** A sixth preset, which would put the group close
enough to its width that the menu starts appearing at ordinary font scales. The
answer then is the date sheet's grid, not a return to a row that truncates.

---

## D-027. Recurrence gains an interval, a weekday set and an end condition

**Decision.** The board's Repeat editor is built now. `Recurrence` stops being a
four-value enum and becomes a rule: a unit, an interval, a weekday set, and an
end condition. `Task` gains `occurrenceNumber`, and the schema goes to
version 10.

**What this supersedes.** D-019 entirely. That entry kept the nine Repeat frames
as a Phase 4 design and out of the code, and this one moves them in. It also
closes `task-details.md`'s "Known gaps", which said "Repeat is designed and not
built", and retires the third bullet of Phase 4 in `ROADMAP.md`.

**Why the deferral ends.** D-019's objection was never that the design was
wrong. It was that "a schema change and a rule engine arriving through a Figma
frame" is what the drift guard exists to catch. That is an argument about how a
change arrives, not about whether it should, and the entry says so in its own
closing line: "when Phase 4 begins this stops being a deferral and becomes the
specification." Pulling it forward changes which pass builds it, and leaves the
substance where D-019 left it.

So the guard is satisfied by arriving deliberately: the schema change is
enumerated below, the counting problem D-019 raised is answered rather than
discovered, and the design is settled on a board that has already been reviewed
frame by frame.

**The counting problem, which D-019 named and was right about.** It warned that
"an end date and an occurrence count both need to know how many occurrences have
already happened, and nothing records that today". The chain of `spawnedFromId`
links looks like the answer and is not: counting it means walking every ancestor
on every completion, and deleting any one of them breaks the count silently.

`Task.occurrenceNumber` records the position directly. It is 1 for everything a
person made, the spawned copy takes its parent's plus one, and the series stops
when that number reaches the limit. One integer, no walk, and nothing to
recompute. Existing rows default to 1, which is the conservative reading: an
"after ten" rule set on a task that has already come back six times gets ten more
rather than four. The app has no record of the six and should not invent one.

**Stored rule names do not change.** The unit keeps `DAILY`, `WEEKLY`, `MONTHLY`
and `YEARLY` as its constants even though the sheet now says Day, Week, Month and
Year, because the label is a string resource and the constant is what is written
in the `recurrence` column of every install. Renaming them to match the new
labels would mean rewriting rows to store the same meaning, and version 10 should
touch no row that already exists. Five appended columns, all nullable or
defaulted, on the shape every migration before version 9 used.

**Weeks are counted from Monday, and that is a fixed reference rather than a
display convention.** "Every two weeks on Monday and Thursday" needs to agree
with itself about which weeks are the on-weeks, so the interval is counted in
whole weeks from the Monday of the anchor's week. A locale-dependent week start
would move the rule when the device's locale changed, which is the class of bug
`AGENTS.md` keeps out of the domain. Which day the chips are drawn from is a
separate question and stays with the UI.

An empty weekday set means the anchor's own weekday. The set is only offered for
the Week unit, and a rule that matches no day at all, and so returns nothing
forever, must not be reachable.

**Two mechanisms, and they are not redundant.** The sheet will not let the last
selected chip come off, which is what the board's own frame name asks for:
"Weekday selection, visible ONLY when repeat unit == Week, require >=1 selected
day". The fallback in the domain stands behind it, because the sheet is not the
only way a rule with no days can exist: every weekly rule written before this
entry has an empty set, and reads as the day its task is anchored to.

So the sheet enforces the board's rule going forward and the fallback reads the
rules that already exist. Removing either leaves a hole. Without the sheet rule a
user can build a weekly rule that names no day; without the fallback every weekly
task in every install before version 10 stops repeating.

Selecting a first day from none is allowed. It is going back to none that is
refused, and it is refused by nothing happening rather than by an error or a
disabled chip: a greyed-out chip would say the day was unavailable when it is
precisely the day that is chosen.

**Occurrences are counted, not banked.** `nextOccurrence` still returns the first
date strictly after today, so finishing three weeks late produces the next date
still to come rather than three copies of what was missed. That rule predates
this entry and the interval does not change it.

**What the board does not answer, and the smallest reading of it.** No frame
shows how a repeating task stops repeating. The main state has Every, Days, Ends
and Save repeat, and none of them clears the rule. The sheet keeps a "Doesn't
repeat" action beside Save, shown only when the task currently repeats, which
follows the date sheets: their grid spends its first cell on "No date" for the
same reason. A control offered when it can do nothing is one the user cannot tell
worked, which is the rule `DatePresets.kt` already argues from.

**Ends is not in the row summary, and the board has since said so better.** The
board now carries a summary line under the sheet's title, and 471:6537 writes it
with the horizon ("Every week on Mon, Wed and Fri, until Aug 17") while 471:6668
writes the applied Plan row without one. So the split is the board's, on clearer
evidence than the frame this entry originally cited.

The reason holds either way: the row answers how often, and when it stops is a
thing you open the sheet for. What the sheet adds is that it is the one surface
where the rule is being *built*, and the one that does not commit as you go, so
its subtitle is a preview of what Save will write.

**Three amounts, not two flags.** `RecurrenceStyle` replaces the boolean that
told the notification apart from the screens. `Compact` is the rows, `Sentence`
is the notification, `Full` is the sheet subtitle. A second boolean would have
made four combinations of which three mean anything.

**The rows stay compact, and this is a deliberate divergence from the board.**
471:6668 draws the Plan row reading "Every week on Mon, Wed and Fri"; the code
writes "Mon, Wed and Fri". Two reasons. The value sits right-aligned beside a
label with a chevron, next to siblings reading "Today", "None" and "45m", and the
long phrase makes that one row visibly taller than the four above it in a longer
language or at 200%. And `PlanRow`'s own rule is that the value "states what is
set rather than naming the sheet it opens": the other four rows carry no verb,
and "Repeat / Every week on..." puts one back.

**Two weekday sets are named rather than listed, and one of them is Daily.**
Every day selected at an interval of one is a daily rule: `RecurrenceRuleTest`
asserts the two produce the same date on every day of a fortnight, so "Mon, Tue,
Wed, Thu, Fri, Sat and Sun" was a long way of writing "Daily" and the Plan row
was spending its whole width on it. The interval is load-bearing, because all
seven days every *other* week is seven days on and seven off, which is not daily
and is asserted not to be.

The weekend and its complement are named too, because five days spelled out is
nearly as long as seven and is not rescued by being daily. **Which days those are
is ICU's answer and not Monday to Friday**, on the same reasoning as the list
join: a good deal of the world does not take Saturday and Sunday off, and
hardcoding the English-speaking week would be the same mistake one screen along.
Android's ICU exposes `isWeekend()` rather than the day-type query that would
answer this directly, so each day is probed at both ends, and a day the locale
counts as weekend for only part of itself counts here.

Each name has a standalone and a phrase form, because the row shows "Weekdays"
on its own and the subtitle reads "Every week on weekdays". English capitalises
one and not the other; a language that does not can translate both the same way.

**Naming describes, it never rewrites.** The stored rule stays weekly with seven
days. Normalising it would throw away the chips: the user taps seven and reopens
the sheet to find the Daily unit, no Days section, and no way to drop a single
day without rebuilding the rule. The row shows what it means and the sheet shows
what it is, which is the split `RecurrenceStyle` already draws.

**The count clause reads "for 10 occurrences", not "10 times in total".** Three
reasons, and the third is the one that decided it. It parallels "until 17 Aug",
which fills the same slot, so the reader meets two phrases of the same shape.
"Occurrence" is the word the control that sets it already uses, along with its
help text and the sheet's own footnote, so the summary stops changing vocabulary
halfway down the sheet. And "in total" is a claim the data cannot support: a task
that crossed the version 10 migration counts from one however many times it had
already come back, so "ten in total" would vouch for a history this entry
deliberately declined to invent. "For" bounds the span without saying where it
began.

**Weekday lists are joined by `android.icu.text.ListFormatter`.** A list is not
commas with an "and" before the last item in most of the world; the separator,
the conjunction and its placement all vary. ICU ships with Android and knows
this, and the alternative was inventing a rule for English and exporting it. It
also means the app renders the serial comma where the locale wants one, so an
en-US device reads "Mon, Wed, and Fri" where the board, drawn in en-GB, reads
"Mon, Wed and Fri". Both are correct and neither is ours to choose.

**What would reverse this.** Nothing about the model. If the weekday set turns
out to want a monthly counterpart ("the second Tuesday"), that is a new unit
rather than a change to these, and it should be its own entry.

---

## D-028. Settings and local continuity move ahead of the widget

**Decision.** Build the already-settled Settings and Backup & restore work now,
before Phase 4's Glance widget. This changes ordering only: D-024's closed list
still governs the Settings screen, and Phase 4's widget remains outstanding.

**Why the order changes.** The Settings and continuity panel was explicitly
requested from the reviewed board. Unlike an opportunistic adjacent feature,
its product behavior is already bounded by D-024 and `docs/design/settings.md`:
four rows, a three-choice theme dialog, persistent appearance choices, and a
user-controlled JSON backup that replaces local data on restore.

**What does not move.** Nothing else from Phase 5 moves with it. The full
accessibility and dark-theme audits, Play listing, privacy policy, and release
work stay in Phase 5, and this entry does not mark Phase 4 complete.

**Continuity is explicit and local.** The backup includes every task field and
the two appearance preferences. Reminder delivery history is not copied: it is
evidence about the device that produced it, and restoring it onto another phone
would make Reminder health report the old device as though it were the new one.
The restore therefore clears that history while replacing the task set.

**No new dependency.** Appearance choices use Android's `SharedPreferences`,
and the backup uses the platform JSON reader/writer plus the Storage Access
Framework. The Android SDK already supplies the required persistence and file
ownership, so adding DataStore or a serialization library would buy another
dependency without adding product capability.

---

## D-029. Reminder health has one home in Settings

**Decision.** Remove Reminder health from the app-bar overflow. Settings is its
single permanent navigation entry; the Reminder health screen and route remain
unchanged.

**Why this supersedes the two-entry rule in `docs/design/settings.md`.** That
rule was written before Settings existed and treated a future Settings row as
an additional safety entrance. Once both entrances were present, they became
two identically labelled routes to the same room. The overflow gave no signal
that one was a shortcut, and nothing differed after the tap, so the duplicate
added a navigation choice without adding capability.

Reminder reliability remains the product's highest-severity concern. It is
still the first row in Settings, while contextual warnings and reminder deep
links may take a user directly to the health screen when action is needed. A
permanent duplicate in More is not the mechanism for communicating failure.

**What stays in More.** Logbook and Settings. Both are distinct rooms; neither
duplicates an entry inside the other.

## D-030. A reminder resolves forward, and a past moment is never stored

**Decision.** A reminder is never saved for a moment that has already gone.
Where the day was not chosen deliberately, the reminder resolves forward to the
next occurrence of that time of day and the app shows the day it landed on.
Where the day *was* chosen deliberately, the app refuses to save and says why.
`reminderTrigger`'s clamp is untouched.

**The bug this fixes, and why it was invisible.** Two reminders on a OnePlus 8T
were recorded as placed at 03:20:34 and 11:00:49 and delivered five seconds
later, hours after the times they were set for. `reminder_deliveries` holds
`scheduledWallAt` beside `arrivedWallAt`, so those are measured moments rather
than an inference from a notification log. This read as a manufacturer delaying
an alarm and was not: the alarms were placed correctly and were already overdue
when they were placed.

`ReminderDialog` selects a time and nothing else. `TaskDetailsScreen` supplied
the day from `task.reminderAt?.toLocalDate() ?: task.scheduledDate ?: today`,
and its own comment recorded the gap in writing: "`PRODUCT.md` keeps a reminder
independent of a scheduled date, but nothing on this dialog moves it off that
day". So a task scheduled for a day now past could only take a reminder on that
past day. `reminderTrigger` then clamped it to now, and it rang immediately.

**The dialog already contradicted the product.** `PRODUCT.md` says "A reminder
is independent of a scheduled date and of a due date". A dialog with no date
cannot express that, so this is not a refinement of a working control. The
editor could not say the thing the product requires it to say, and the missed
reminders are what that gap looks like from outside.

**There were two doors, not one.** `CaptureParser.reminderAt` built its moment
as `LocalDateTime.of(date ?: defaultDate, time)` with no comparison against the
clock, so "call the dentist at 3pm" typed at 4pm captured a reminder three
hours gone. A fix confined to the dialog would have left Quick Add doing it.
Nothing anywhere on the write path compared a reminder against now; the only
such comparisons, `Reminders.kt` lines 59 and 86, classify pending against
missed *after* the row is stored, which is the machinery that then delivered
these immediately.

**Why forward rather than refusal, for the implicit case.** `date-parsing.md`
already promises that "no supported input ever resolves to the past", and
`DateParserTest` walks a full year to defend it. That promise covers the day
peel only. The time peel rides on `defaultDate` and breaks it. Resolving a
reminder forward is therefore not a new rule, it is the existing rule reaching
the half of the parse that escaped it: "next Tuesday" never means last Tuesday,
so "at 3pm" must not mean three hours ago.

**Why refusal, for the explicit case.** Choosing a day on the calendar and then
a time already gone is a request for something impossible, made deliberately. A
promise the app has already decided it cannot keep should not be stored.

**What TickTick does, and which parts were taken.** Checked on a device at
18:35. Typing "Call dentist at 3pm" resolves to a chip reading "Tomorrow,
3:00PM" before saving, with the parsed words highlighted and cancellable.
Date, Time, Reminder and Repeat live in one sheet. Selecting today with the
time still 3:00PM greys the Time and Reminder rows, and confirming produces
"Oops...the reminder you set is invalid because it is already overdue."

The forward resolution and the single control are taken. The grey-out is not.
It warns at confirmation rather than at the moment of choice, and it stores the
task carrying a reminder the app has already judged dead. `PRODUCT.md` calls a
reminder "a promise that the app will interrupt the user at a specific moment"
and a missed one the highest-severity bug in the product; storing a promise
known to be unkeepable does not meet that bar, whatever the toast says.

**The shape: two rows, not two pickers in one window.** The first build put day
presets inside `TimePickerDialog`, above the clock. It worked and it was wrong.
The day took the top third of a window that exists to pick a time, the calendar
button was the heaviest control up there despite being the least-used option,
and asking for the full width made the dialog span the screen and lose its own
inset. Reusing D-018's grid was defended as consistency; the components were
consistent and the density was not.

`ReminderSheet` replaces it: a `ModalBottomSheet` with a Day row and a Time row,
each opening its own picker, then Save. That is TickTick's structure, it is the
one `RepeatSheet` already uses for a value made of several fields, and the rows
are the same `PlanRow` the screen behind them is built from. The day presets
move into a substate reached from the Day row, on the same terms as Every and
Ends.

No summary line above the rows. Two rows reading Tomorrow and 9:00 AM say the
whole thing, so a line reading "Tomorrow, 9:00 AM" over them is the restatement
D-026 refused for Duration's hero readout. `RepeatSheet` has one because four
fields compose into something the controls do not show separately; two do not.

**A Save, for the reason `RepeatSheet` has one.** D-018 commits as you go
because every other row is one field set by one choice. A day and a time only
mean something together, and writing each as it is tapped would push the task
through saved states nobody asked for, each one rescheduling an alarm.

**The day pane offers two presets, not D-018's four.** Today and Tomorrow. The
far preset that suits a scheduled date does not follow here, because "this
weekend at 3pm" is a vague thing to ask of an interruption in a way "tomorrow at
3pm" is not. Clearing has its own control on the pane behind, which is what
frees both cells for days.

**What was not taken.** A reminder stays a moment of its own rather than an
offset from a due date. TickTick's model would make this bug unreachable by
construction, and it contradicts `PRODUCT.md` head-on. It also inverts the
product: here the interruption is the feature, not a satellite of a deadline.

**The clamp stays.** `reminderTrigger` maps an overdue reminder to now, and that
is for the phone having been off, a battery optimiser having dropped an alarm,
or a timezone change. Those are reminders that were correct when set. Removing
the clamp to fix reminders that were wrong when set would trade a noisy failure
for a silent one, which is the wrong direction in this product.

**The boundary.** Forward resolution applies when the moment is strictly before
now. A reminder set for exactly the current minute is kept and fires at once,
matching `Reminders.kt`, where "a reminder due at exactly now counts as missed
rather than pending".

**This unblocks Reminder health, which was starved of evidence by the bug.**
Found while verifying on the 8T and worth recording, because it is not obvious
from either side alone.

`backgroundWorkState` clears its `SleepStandby` warning only on
`EvidenceOfHealth` consecutive deliveries that `testsIdleDelivery`, which means
`scheduledAhead >= EvidenceHorizon`, an hour. A reminder created in the past is
clamped by `reminderTrigger` to now, so it is placed with `scheduledAhead = 0`
and, by `ReminderDeliveryTest`'s own rule, tests nothing.

Both delivery records on that phone read `scheduledAhead = 0`. So the check had
no evidence at all and would have sat at `Warning` for ever, on a device whose
reminders it could never learn anything about. The screen was not wrong; it was
being fed reminders that proved nothing. Reminders now placed hours ahead are
the first this install has recorded that can clear it.

**What the health screen was right about, and what it was not asked.** Those two
records are not reported as missed, and that is correct rather than lucky.
`lateness` is measured against the moment the alarm was aimed at, and for a
clamped reminder that moment is the placement, so both read five seconds late
rather than six and eleven hours. The app does not blame the device for its own
scheduling. That property was already there and is what let this bug hide: the
health screen honestly reported nothing wrong, because from where it stood
nothing was.

## D-031. The home widget speaks only when it has grounds

**Decision.** Phase 4's widget is one design with a conditional lead, not two
sizes distinguished by how many rows they hold.

- It leads with a single task and its reason for `ResumePaused` and
  `ReminderPassed` only. For `NoTimeToday` it shows the plain list.
- It has two quiet end states, *everything done* and *nothing scheduled*. The
  app does not distinguish them and the widget has to.
- A completed row stays in place, checked, until the next refresh, rather than
  vanishing on tap.
- No "N tasks left". A count appears only to disclose rows that did not fit.
- It follows the system theme, not the Settings theme choice.
- Rows keep the app's anatomy: the trailing column is the duration, and overdue
  is carried by the date in the metadata line, not by a word in that column.
- Hierarchy inside a row comes from type size and weight. No accent colour and
  no reduced opacity, for the reason below.

Size changes how many rows fit and nothing else.

**Today is consulted, the widget is glimpsed, and that is the whole argument.**
Opening the app is a decision: the user has said they are dealing with their
tasks, and D-012's card answers the question they just asked. A widget is
crossed involuntarily while unlocking the phone for something else. No question
was asked, so the surface has to earn the right to assert. The bar for speaking
up scales with how much the user asked to be there.

**Why the widget cuts the threshold higher than the app does.** `focusNow`
already refuses to always have an answer, and D-012's reason is that a card that
always found something to say "would be asserting without grounds". The
threshold that satisfies that in-app does not satisfy it on a home screen.
`focusNowReasonOf` matches `NoTimeToday` for any task scheduled today carrying
no reminder, which on an ordinary day is most of them. A widget leading on that
reason would point insistently at a task all day whose entire claim is that the
user put it on today and said nothing about when. In-app that is a weak but real
answer to a question. Ambient and unavoidable, it is the motivational noise
`PRODUCT.md` principle 7 excludes.

No new rule is needed for this. `FocusNowReason` is declared in priority order
and is `Comparable` by it; the widget reads further up the same enum.

**The two reasons kept are the two where the widget does something no other
surface does.** `ReminderPassed` is the product's promise, and a notification is
transient while a widget is permanent, so the widget is the durable backstop for
a reminder that was swiped away. D-005 is the whole reason that matters.
`ResumePaused` is better still: the session survives the sheet because the card
points at it, and on a home screen Resume becomes one tap instead of open, find
the card, tap.

**Completion has to leave evidence, because on this surface it otherwise erases
its own.** A widget showing outstanding work removes a task the moment it is
checked. Three 48dp targets, read while walking, and a mis-tap makes the task
disappear with no snackbar and nothing to say which one moved. Recovering means
opening the app and expanding a Completed disclosure that is collapsed by
default. `TaskListViewModel.kt` states the app's view of that trade already: one
failure is unrecoverable and invisible, the other costs a glance. Keeping the
checked row in place until the next refresh is also honest about the platform,
where the update is asynchronous anyway. Whether the checked row can be tapped
to undo is left open; showing what happened is not.

**Everything done and nothing scheduled are different days.** Today needs one
empty state because the Completed section is visible on the same screen. The
widget has no such section, so a single state would tell someone who just
finished six tasks that nothing was scheduled, which reads as the app forgetting
their day.

This is also what replaces the count. The honest case for "3 tasks left" is the
glance: one number answers *am I on top of things* faster than three rows of
text. A permanent home-screen burn-down that never reaches zero and refills each
morning is the thing that makes task apps tiring, and an end state that looks
calmly different answers the same question with no number. Where a count does
appear it follows the app's existing rule rather than a new one:
`CompletedDisclosure.kt` sets the default as "no count badge" and makes its own
band a narrow exception because it counts what is collapsed out of view. So the
widget may say what is hidden, never what is already on screen.

**The theme follows the system, and D-024 is what settles it.** A widget sits
among other widgets on the launcher's surface, where looking wrong beside its
neighbours costs more than differing from an in-app setting the user is not
looking at. The obvious escape hatch is a widget-theme row in Settings, and that
is closed: D-024 fixed the list at four rows and D-028 built it. The Settings
theme choice governs the app; the widget is not the app.

**Colour cannot carry meaning on this widget, and dynamic colour is why.**
Measured against the board's own variables, `Tertiary` on `Primary Container`
gives 4.99:1 in Light and 5.51:1 in Dark, and 3.53:1 in Wallpaper warm, which is
below AA for a 12sp line. `Error` is no better at 3.61:1. The warm
`Primary Container` is a mid-luminance orange, so no accent clears it, and
reducing opacity makes it worse rather than better: `On Primary Container` at 85%
falls to 3.88:1. There is no `onPrimaryContainerVariant` to retreat to.

This is why the overdue cue is the date itself rather than a colour, and it is
the position the app already holds. `TaskRow.kt` treats the overdue colour as
"the second cue" sitting on top of the words, and Today keeps overdue "readable
without relying on the colour" because a band heading names it. The widget has no
band headings, so the words have to do all of it. Row hierarchy comes from 12sp
Regular against 14sp Medium, which costs no contrast at all.

**What the board draws now, and what has to change.** Section 10 holds six
frames, Medium and Compact in Clean Slate, Dark and Warm. They are token-clean
and identical within each size, so the theming is sound and only the content
moves. What changes: the two sizes stop differing by row count, "3 tasks left"
goes, "Overdue" leaves the duration column, and the end states and the leading
card are drawn for the first time.

**What is not decided here.** Responsive sizing and the API 29 and 30 corner and
size behaviour, whether the `+` survives at the smaller size, and whether a
checked row is tappable to undo. Those are implementation shape and belong in
`docs/design/widget.md`, whose absence is why this section drifted from the app
unnoticed and which now carries them.

**What would reverse the threshold.** Evidence that users place the widget and
then ask why it is not telling them what to do. The failure this guards against
is quiet and the failure it risks is loud, so the risk is self-reporting: a
widget that says too little gets complained about, while one that nags gets
removed without a word. If the plain list turns out to read as inert, promoting
`NoTimeToday` is a one-line change to where the widget reads the enum.

**Superseded in part by D-035**, which took the same argument into the app and
removed `NoTimeToday` from `FocusNowReason` altogether. The threshold above is
no longer the widget's own: there is nothing left to filter, and restoring the
reason now means restoring it to the rule both surfaces read.

## D-032. The Focus sheet loses its dismiss button

**Decision.** Remove the chevron from the Focus sheet. Leaving is the drag
handle, the drag, the scrim and the back gesture, all of which already routed to
the same place.

**This does not touch D-015.** That decision is about what leaving *does*, and
leaving still pauses rather than stops. `onDismissRequest` is unchanged and
every remaining route arrives there. What goes is one of the ways in, not the
behaviour behind it.

**Why it was redundant, and this is the part that had never been written down.**
`ModalBottomSheet` is called with no `dragHandle` argument, so Material draws its
default one. The chevron sat directly beneath it. Two collapse affordances,
stacked, eight pixels apart, both meaning "put this away" and both landing on the
same call. `focus.md` argued the chevron "agrees with the gesture: a bottom sheet
is dismissed by dragging down" without noticing that the thing which announces
that gesture was already on screen saying it.

**The board had never drawn the handle**, which is how this survived review. Its
Focus frames showed a 64dp header holding a chevron and nothing else, so on the
board the chevron looked like the only way out, and in the app it was the second
one. The frames now draw the handle and no header.

**What it costs, stated plainly.** A 48dp target with a content description
becomes a 32×4dp bar without one. That is a smaller and less obvious affordance,
and for a screen reader the labelled control is replaced by the sheet's own
dismiss action, which Compose publishes and TalkBack offers. Nothing becomes
unreachable, and back still works, but this is a reduction in how loudly the exit
announces itself rather than a free simplification.

**A test went with it.** `everyStateOffersAVisibleWayOut` existed to assert the
chevron was present in every state. What it guarded now belongs to Material
rather than to this app, so the assertion moves into
`leavingPausesRatherThanStopping`, which triggers leaving through the sheet's
dismiss action and therefore proves the exit exists and pauses in one place.

**What would reverse this.** Users not finding their way out of Focus, which
would show up as sessions left running rather than paused, or as the sheet being
backed out of rather than dismissed. The drag handle is the whole exit now, so if
it is not enough the answer is a labelled control, not a second gesture.

---

## D-033. Today gains a finished-day state, and it supersedes the one-empty-state rule

**Decision.** Today draws a distinct state when the day had work and all of it
is complete: the sitting-happy cat, "All done for today", and a supporting line
sending the user to the Logbook. It is separate from `today_empty_*`, which
stays for a day that never had anything on it.

This supersedes the claim in `docs/design/widget.md` that "Today needs one empty
state because its Completed section sits on the same screen." That sentence was
written to justify the widget having two states, and it asserted something about
Today as a side effect of arguing about the widget. Today now has two as well.

**Why.** The reason given for one state was that the Completed section keeps a
finished day legible. It does, but not as an answer to the question the screen
is asked. Opening Today on a finished day currently shows a single collapsed
"Completed · 6" disclosure and nothing else, which is a control rather than an
answer, and it is the same screen you get for a day where you completed one
thing out of seven. The state that is worth naming is the one where nothing is
outstanding, and that state had no words on it.

The product owner asked for it, and the pose existed on the board before the
implementation did.

**It heads the list rather than replacing it.** The first implementation drew
the finished-day state in place of the whole body, the way the other empty
states do, and that was wrong. Today's instrumented tests caught it:
`undo_reopensTheTask` completes the only task, opens the Completed disclosure
and reopens the row from Today, and with the body replaced that disclosure was
gone. The test was encoding something real. Once the undo snackbar times out,
reopening a task finished today would have meant leaving for the Logbook, and
D-012 built that disclosure precisely so it would not.

So the day being finished is worth saying, and it is said above the rows rather
than instead of them. `TaskListDoneHeader` is the same statement as
`TaskListEmptyState` sized to its content, sharing one body composable so the
two cannot drift.

A consequence worth noting: this is the one state in the app that is not an
empty state and draws like one. It is a header over a list that has content.
That is the honest description of a finished day, which is full of completed
work rather than empty.

**It is not a celebration, and the line matters.** `PRODUCT.md` principle 7
rules out unnecessary celebrations, and this sits close to that line. What keeps
it on the right side is that the copy states a fact and points somewhere, the
same rule every other supporting line follows. A pose that reads as content
rather than triumphant is doing the same job the sleeping cat does on an empty
day. If the copy ever congratulates, principle 7 is what it broke.

**What would reverse this.** Evidence that the finished-day state is read as the
app losing the day's work, which would show as users opening the Logbook to
check their tasks are still there rather than to read them.

---

## D-034. A failed read says so, on every list

**Decision.** `TaskListViewModel` catches the stored read once and carries the
failure as a value. Every list derives from the caught stream, and a failure
draws `TaskListErrorState`: the question-mark cat, the headline in the error
role, the copy from `expressive-components.md`, and a Try again button that
starts a fresh read.

**Why.** Room hands the app a cold `Flow`, and a `Flow` that throws is finished.
Before this, each of the five list views subscribed to `observeTasks()`
separately, so a failed read left every one of them sitting at its initial empty
list. The user would see "Nothing scheduled for today" on a day full of tasks.

That is the worst class of bug this product has, one step below a missed
reminder and for the same reason: the app quietly asserting that work does not
exist. It was not a hypothetical. The error state had been designed, written
down in full, and described in `expressive-components.md` as though it were
built, and none of it existed in code.

**Retry starts a read rather than resuming one.** `catch` ends the flow it
guards, so there is nothing to resume. `retryRead` increments an attempt counter
that `flatMapLatest` turns into a new subscription, which is also why the caught
stream sits behind a `MutableStateFlow` rather than being a plain `catch` on the
repository.

**The failure carries no tasks.** `TaskRead.Failed` holds an empty list rather
than the last good one, because a screen drawing stale rows beneath an error
message makes two claims at once and the older one cannot be checked.

**The copy must never mention a connection.** Catimo has no account, no sync
and no backend, all three permanently out of scope in `PRODUCT.md`, so every
read is local. Earlier draft copy on Inbox and Upcoming read "Check your
connection and try again", which sends the user to fix something that was never
involved and implies a server the app does not have.

**What would reverse this.** Nothing about the state itself. The open question
is whether one shared error is right: all five views read the same stream, so
they fail together, and if a future read is genuinely per-screen this becomes a
per-screen flag rather than one.

## D-035. The Focus now card speaks only for an event, and loses NoTimeToday

**Decision.** `FocusNowReason` loses `NoTimeToday`. The card appears for a paused
session or a reminder that has passed, and for nothing else. A task scheduled for
today carrying no time stays in the "No time set" band, where it always belonged.

This supersedes D-012's third reason. It also ends D-031's split threshold: the
app and the widget now read the same rule, and the widget's filter goes with it.

**What changed the answer.** D-031 made this argument fourteen entries ago and
stopped at the app boundary. It cut `NoTimeToday` from the widget because a task
"whose entire claim is that the user put it on today and said nothing about when"
is not enough to assert on, and it defended keeping the reason in-app on the
grounds that opening Today is a question being asked, so the card may answer it.

That defends answering. It does not defend answering with the label already on
the screen. Three things become visible when the case is looked at directly:

- The reason line under `NoTimeToday` reads "Scheduled for today", sitting
  directly above a band labelled "No time set". It is the band label in a
  sentence.
- D-012 promotes the card's task out of its band, so under this reason the card
  *is* the first row of the band immediately below it, lifted out and drawn at
  double size. Nothing is added but weight.
- The grounds apply equally to every task in that band. `focusNow` then picks the
  first by list order and states a reason that would be equally true of the next
  four. That is a ranking with nothing to say why its head is its head, which is
  what D-004 removed the queue for. D-012's defence against being the queue again
  was that the card states its reason on screen; a reason that cannot distinguish
  its task from four others does not clear that bar.

**What survives is the two reasons that name an event.** A paused session is not
derivable from any list on any screen: no row can say that work was started and
has seventeen minutes left, and per `FocusNow.kt` the session surviving the sheet
depends on this card pointing at it. A reminder that fired and was not acted on
is `PRODUCT.md` principle 1's defining failure, and the app promoting it is the
app admitting it. Both are things that happened. Neither is a position in a list.

**The cost, taken deliberately.** On a day with no paused session and no passed
reminder there is no card, and for a user who schedules days rather than times
that is most days. A card seen rarely is a card that is learned slowly, and the
Focus button on it is Today's only one-tap route into a session, so that route
gets rarer too.

Accepted, because principle 2 asks Today to make the answer obvious within
seconds and the bands already do that by ordering: past, present, future, with
the answer at the top of the screen. Against that baseline the `NoTimeToday` card
was contributing size, not information. A card that is right whenever it appears
is worth more than one that appears daily and guesses, and the guessing one
teaches the user to skip the top of the screen.

**What this does not reopen.** Not the queue, still. Not Anytime, whose vocabulary
D-002 cut and whose band label D-012 deliberately worded in plain English. The
bands are untouched, including their order and the Completed disclosure.

**What would reverse this.** Evidence that Today reads as inert for users who set
no reminder times, or that the card appears too rarely to be understood when it
does. Both are observable without instrumentation the app does not have: the
first shows up as users not knowing Focus exists, the second as the card being
ignored on the days it fires. Restoring the reason is an enum value and a branch.

## D-036. The widget fills the space it was given, not the breakpoint it matched

**Decision.** `rowCapacity` is derived from the widget's reported height rather
than looked up from a table keyed on `WidgetLayout`. The enum goes with the
table; the responsive sizes D-031 declared stay exactly as they are.

    available = height - header - bottom inset - disclosure
    available -= lead card + its gap, when there is a lead
    capacity  = floor(available / row height)

The lead card and the row height are both multiplied by the font scale, which is
what used to be spelled as a `largeText` branch.

**Why.** D-031 declared two responsive sizes, 288x190 and 364x266, and the model
then treated them as the only two shapes a widget can be. A widget is resized by
dragging, so most real ones are neither. The failure it produced was reported
from a phone: a widget clearly taller than Compact but a little narrower than
Medium fell to Compact, and Compact with a lead card is zero rows, so more than
half of a large widget was empty container with one card floating at the top.

`layout` was the wrong input. Height is what row capacity is a function of, and
width had a vote purely because it shared a breakpoint with height.

**This does not reverse D-031.** That entry's argument was against branching on a
launcher name or a cell count, in favour of sizes reported by the platform.
Reading the reported height is the same argument carried one step further: it is
still the platform's number, and it is now the number the calculation actually
depends on. The two `DpSize`s remain, because Glance needs them to pick which
RemoteViews it builds.

**It reproduces the old table exactly at both declared sizes.** All eight
combinations of the two sizes, lead or no lead, and normal or large text come out
at the counts the hand-tuned `when` returned, which is what makes this a
generalisation rather than a retune. `FocuslistWidgetModelTest` pins those eight
so a change to any constant has to admit which case it moved.

**The disclosure is reserved whether or not it appears.** It costs 20dp on a
widget that has nothing to hide. It buys that `+N more` can never be the thing
that gets clipped, and the disclosure is the only thing on the surface that says
rows exist which are not being shown. D-031 already refuses "a count of anything,
beyond disclosing rows that did not fit"; this keeps the one count it allows
legible.

**What would reverse this.** A launcher that reports a height the widget does not
get, which would show as clipped rows rather than as empty space. The heights
here are the ones the composables declare, so the check is whether those stay in
step: a row that changes height and does not change `RowHeight` puts the two out
of agreement, and nothing but a render will say so.

## D-037. Task Details puts its two actions in a floating toolbar

**Decision.** Start focus and Delete move into one `HorizontalFloatingToolbar`,
pinned bottom centre. The full-width Start focus button at the foot of the
scroll is removed, and so is the app-bar overflow that held Delete. The
checkbox beside the title stays a checkbox.

**What this supersedes.** Three things, and each was argued rather than
assumed.

`expressive-components.md` lists `FloatingToolbar` under "Do not introduce,
unless a later product decision explicitly requires one", noting the exclusion
was "because Catimo has no use for them, not because they are unsound".
This is that decision. The list also still forbids `ButtonGroup`, which D-026
introduced through the same escape hatch and nobody removed from the list; that
line needs correcting either way.

D-022 put Delete in an overflow and rejected every alternative. Its reasoning
is kept where it still holds and named where it does not, below.

`task-details.md` described the third region as "One full-width `Start focus`".
It is now the toolbar's trailing action.

**The complaint that started it.** An overflow whose only item is Delete
promises options it does not have. Three dots say "there are more of these", and
there is one. D-022 argued well against an icon button, against a button beside
Start focus, and against a confirmation dialog, but never against the menu
itself being a menu of one.

**What D-022 got right and this keeps.** Delete must not sit one tap from Back
in the corner a thumb reaches for when leaving; it is not there. It must not
carry the same weight as the screen's payoff; Start focus is the toolbar's
attached FAB and Delete is a plain icon button inside it, which is Material's
own arrangement for a bar with one action that outranks the rest. A
`FilledIconButton` was the first build and only approximated it: the same fill,
none of the size or the separation, so the two read as equals with one tinted
differently.
Deletion stays a soft delete raising the same single undo offer every list
raises, with no confirmation dialog, because a dialog is a second question after
a reversible answer.

**What D-022 got right and this gives up.** "An unlabelled trash icon is the
least legible form of the most destructive action." That is still true and this
accepts it. Two things make it affordable: the delete is soft and undoable, and
the icon carries `error` as it did in the menu, so colour remains the second cue
even though the word is gone. The word survives as the content description,
which is what a screen reader announces.

**Order: Delete leads, Start focus trails, which inverts the menu rule and
applies its reasoning.** `expressive-components.md` orders the row menu
"constructive before destructive, so the thumb does not land on Delete". In a
vertical menu the thumb lands nearest the bottom; in a horizontal bar it lands
nearest the reaching side. Putting Start focus at the trailing end is what keeps
the thumb off Delete here, and `FloatingToolbarHorizontalFabPosition` defaults
to exactly that, so the arrangement is the component's own rather than one
imposed on it.

**Why a floating toolbar rather than a docked one.** M3 separates them by
purpose: docked carries global actions repeated across pages, floating carries
contextual actions belonging to the body content. These two act on the one task
the screen is about. Task Details also has no navigation bar, so the rule
against pairing a docked toolbar with one does not arise either way.

**What this does not fix, and the honest cost.** Start focus loses its label,
and `task-details.md` recorded that the same button without its glyph "was
pressed by people meaning to close the page". The glyph solved a filled pill
being read as a commit control; a small play icon in a floating bar is not
open to that reading, so the specific trap is gone. What replaces it is lower
discoverability: a first-time user sees two glyphs rather than a worded action.
That is the trade this decision makes, and if it turns out to cost more than the
overflow did, the entry to supersede is this one.

**Clearance.** The scrolling column reserves `FocuslistDimensions.FabClearance`
beneath its last Plan row, on the same terms the lists reserve it under their
FAB: a floating control that covers the thing it acts on is worse than one that
scrolls.

## D-038. The full-screen alarm is deferred past 1.0, and the board frame stops looking built

**Decision.** Catimo ships 1.0 without `setFullScreenIntent`. The
`notify/Full screen alarm` frame is marked as a design held rather than as
behaviour, and the item moves to the After 1.0 list in `ROADMAP.md`, ordered by
review evidence like everything else there.

This does not decline the feature. It dates it.

**Why it looked ready.** D-005 commits to "full-screen intents where warranted"
inside the API layer, the board draws the frame, and 202 of the reviews in the
study ask for an alarm rather than a notification, which is the second largest
ask behind missed reminders themselves. `reminders.md` had already recorded that
none of it exists in code. A commitment, a drawn frame and no implementation is
the state that makes a thing look done to the next session.

**It is a feature, not a notification flag.** The warranting rule has to be
per-task, for the reason below, and per-task means a column on `Task`, so schema
version 11 and a migration; a field in the backup codec, whose format is at
`Version = 1` and which rejects an unknown version by design, so the file format
moves with it; a Task Details row and its sheet; board frames for both; and an
answer for what Quick Add and the widget do with the flag. Then the alarm
surface: an activity over the lock screen, `setShowWhenLocked`,
`setTurnScreenOn`, keyguard dismissal, and a permission flow with a degraded
path. That is a phase. It was never a Phase 5 bullet.

**It is the riskiest available change to the one subsystem that cannot break.**
`CLAUDE.md` puts a reminder that does not fire above a crash, because a crash is
visible and a miss is not. Adding a second delivery path through the reminder
pipeline immediately before the first public release inverts the sequencing the
whole roadmap rests on. Phase 3 ran before Phase 4 to avoid this exact shape of
mistake, and the argument does not weaken because the feature is wanted.

**A contested declaration is worst on a first submission.** Play's carve-out is
"apps whose core functionality is a high-priority use case of setting an alarm
or receiving phone or video calls". Whether task reminders are that is genuinely
arguable, and arguing it on the first submission spends the riskiest declaration
at the moment the app has the least standing and no install base to point at. On
a later update a questioned declaration costs a feature; on the first one it can
hold the launch.

**Some of the demand is already met, which is easy to miss.** The reviews asking
for an alarm are mostly people whose notifications were silent, late or absent.
Catimo answers that with `IMPORTANCE_HIGH`, `CATEGORY_REMINDER`, exact alarms
that survive a restart and a clock change, the OEM layer, and the trust layer no
competitor ships. The full-screen intent is one expression of alarm-like, not
the whole of the ask, and shipping it is not the same as answering the
complaint.

**The door is demonstrably open, and this is worth recording while it is fresh.**
TickTick 8.1.3.6, installed from `com.android.vending` on the emulator, declares
`USE_FULL_SCREEN_INTENT` alongside `SYSTEM_ALERT_WINDOW` and `WAKE_LOCK`, and
holds it `granted=true` at `targetSdk=37`, the same target Catimo builds
against. So a task app rather than an alarm clock does hold this permission
through Play today. What the dump cannot say is how: the package app op reads
`default` while the UID mode reads `allow`, and the op changed about four days
after install, which hints at a user grant rather than a Play pre-grant. Read
that as a lead, not a finding.

**The failure mode is a floor rather than a cliff**, which is the other half of
why deferring costs little. Since 22 January 2025 a non-qualifying app is not
refused the permission, it is merely not pre-granted: it prompts through
`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, reads
`NotificationManager.canUseFullScreenIntent()`, and degrades to the heads-up
notification the app already ships. Nothing about waiting makes the eventual
build harder.

**What warrants one, when it is built.** Per-task opt-in. A reminder is not
automatically an alarm, and an app that decides for the user which of their
tasks may take over the device is making the claim D-031 refuses on the widget:
speaking loudly on grounds that are equally true of everything else. Per-task is
also the honest thing to write on the declaration, because it makes the
alarm-grade path a choice the user made rather than a claim that every task
reminder is an alarm.

**What would reverse this.** Reviews of the shipped app asking for it
specifically, once the reminder pipeline has been observed working in the wild.
That is the trigger the After 1.0 list already runs on, and D-007 says the same
thing in general terms: let the app's own reviews set the order. Nothing about
this entry survives that evidence arriving.

## D-039. The reminder group summary counts what it hides, and nothing else

**Decision.** Reminder notifications carry `setGroup`, and a summary notification
carrying `setGroupSummary(true)` is posted whenever any reminder is on screen.
The summary says how many, and says nothing else. It is cancelled only when the
last reminder has gone, for the reason below.

The summary carries `GROUP_ALERT_CHILDREN`, so the reminders alert and the
summary does not. The default is `GROUP_ALERT_ALL`, which would have the summary
sound on top of the reminder that just sounded, and the inverse setting would
move the alert off the reminders entirely.

The test reminder stays outside the group.

**Why this needed an entry at all.** It is two platform calls, which is exactly
why it could have gone in without one. The product question is not whether to
call `setGroup`; it is what the summary is allowed to say, and that question has
been answered twice already in this app in the restrictive direction.

D-016 kept the Logbook's day grouping and drew the line at "a heading naming a
day and nothing more: a count beside it is the step that turns a record into the
scoreboard". D-031 refuses the widget "a count of anything, beyond disclosing
rows that did not fit". A summary reading "3 reminders" is a count, and a
consistent reading of those two entries has to say why this one is allowed when
those were not.

**It is allowed because it is the exception D-031 already carves out.** The
count is not a measure of throughput and not a judgement about the user's day.
It is the disclosure of what the collapsed stack is hiding, which is the one
thing a collapsed stack cannot say for itself, and the same reason D-031 lets
`+N more` survive on the widget. Remove the number and the summary is a row that
announces there is something behind it without saying how much, which is worse
than not grouping at all.

The test that keeps this honest: the number counts notifications currently on
screen, not reminders that fired today, not tasks outstanding, not anything the
user would read as a score. If it ever counts something the shade does not
currently hold, it has become a statistic and this entry no longer covers it.

**The board draws the app name and should not.** `notify/Grouped` reads
"Catimo · 3 reminders". Android already renders the app name in the
notification header, so posting that string produces the name twice. The summary
text is the count alone. This is the same class of finding as the duration
strings D-018 normalised: the board drew what it saw on a phone, including the
parts the system supplies.

**The summary is posted from one reminder upward, and the first version of this
entry said two.** The board's frame draws three because three is what the
illustration needed, not because three is a threshold, and two looked like the
honest answer: a summary over a single reminder is a header for a list of one.

Building it proved that wrong in the worst available way. **The notification
service cancels a group's children along with its summary.** So dropping the
summary at one remaining reminder took that reminder off the screen with it, and
the user was never told about work they had not dealt with. `CLAUDE.md` ranks
exactly that above a crash, and it is invisible until a second reminder exists
and the first is handled, so it would have shipped.

The summary is therefore cancelled only when the group is empty, at which point
there are no children left to take down. The cost is that one reminder carries a
summary counting one. SystemUI flattens a group holding a single child and does
not draw the summary over it, so this should never be seen, but that is a
rendering behaviour rather than a guarantee and `ReminderGroupTest` asserts what
was posted rather than pretending to know what was drawn. A redundant header on
some skin is a fair price for not deleting a reminder.

**The open cost: grouping doubles the enqueue rate.** Every reminder now costs
two posts, itself and its summary, and `NotificationManagerService` sheds posts
from a package that exceeds a few per second. Reminders falling due in the same
minute produce that burst. A shed summary is cosmetic and corrects itself on the
next post or cancel; a shed reminder is not, and this halves the burst that fits
before shedding begins. It was found because the test could not get a truthful
count out of back-to-back posts, and confirmed by isolating the case: the same
assertion passes with a pause between posts and fails without one.

Nothing here fixes that, and it is recorded rather than solved because the fix is
coalescing a burst into one summary update, which is a larger change than the two
platform calls this entry is about. If reminders are ever observed going missing
when several fall due together, this is the first place to look.

**Why the test reminder is excluded.** It exists so someone can watch it arrive.
Putting it in the group makes it eligible to be collapsed behind a summary at
the exact moment its whole purpose is to be seen, and it would make the count
say two when the user has one real reminder. `reminders.md` already calls it
"deliberately plain: no Done, no Snooze, nothing to act on"; not grouped belongs
on that list.

**What would reverse this.** A shade where the summary is the only thing visible
and the count is read as a workload rather than as a disclosure. That would show
up as users reporting the app nags, and the fix is the summary losing its number
rather than the group being removed.

## D-040. Today carries a reminder health banner, for what the app knows and not for what it guesses

**Decision.** Today draws a banner when `ReminderHealthState` is `ActionNeeded`
or `Missed`, and never for `WorthChecking`, `Ready` or `Checking`. It cannot be
dismissed, it sits above the day's work in both the populated and the empty
layout, and tapping it opens Reminder health.

**This is new work, not a repair.** No such component existed. `TodayScreen` read
no health state at all and `ReminderHealthState` had exactly one consumer in the
app, the health screen itself. What made this look like a regression is that
`expressive-motion.md` already carried two entries about the banner's motion,
including a paragraph arguing at length that it must not slide in because
"sliding it in would be the app performing its own bad news". A document
described the behaviour of a component that had never been written, which is the
failure mode this file exists to catch: the reasoning was recorded and the code
was not.

**D-029 reserved the mechanism and this is it.** That entry removed Reminder
health from the app-bar overflow and justified the removal partly on the grounds
that "contextual warnings and reminder deep links may take a user directly to the
health screen when action is needed". No contextual warning existed then, so
D-029 traded a real entry point for a promised one. This banner is the promise
being kept, and D-029 needs no amendment.

**Only the two states the app is sure about.** D-021 split the health states by
certainty, and the split holds harder here than it does on the health screen. A
user opens Reminder health to ask a question, and an honest "worth checking"
answers it. Nobody opens Today to ask. `WorthChecking` fires from
`Build.MANUFACTURER` alone, so a banner on it would appear on every OnePlus,
OPPO, Realme, Xiaomi, Redmi, POCO, Samsung, Huawei and Honor from first launch,
permanently, on a phone where nothing may be wrong. That is a banner on the
default screen of the app that can never be cleared by any action the user takes,
which is the precise shape of a warning people learn to scroll past. `Checking`
is excluded for the same reason in miniature: the app has not asked yet, and a
spinner on Today would be the app worrying aloud.

**It cannot be dismissed, because the condition is the dismissal.** Every state
that draws it is fixable, and fixing it removes it. A dismiss control would let a
user clear the notice in one tap and keep the silence that caused it, and
`PRODUCT.md` calls a reminder that does not fire the most severe class of bug in
this product, worse than a crash. The banner is safe to make undismissable
exactly because it is rare and always actionable; if it were ever neither, it
should be removed rather than made dismissible.

**It appears on the empty screen too, which is where it matters most.** Today
renders an empty state instead of the collection when there is no work, so a
banner living only in the list would be missing on a fresh install: the user who
has just declined the notification permission and has not yet added a task is
exactly the user about to set their first reminder into silence. The empty branch
draws it above the empty state for that reason. The failed-read branch does not.
That screen already says one thing and offers one action, and a second message
with a second action underneath it competes for a user who cannot see their tasks
at all.

**It says less than the health screen on purpose.** A label and a sentence, both
strings the health screen already owns, and a chevron. No body copy, no button,
no list of checks. Its job is to get the user to the screen that can fix the
problem, not to become that screen on top of their day. Reusing the health
screen's own strings is what stops the same fact being phrased two ways in two
places, which is how "Catimo cannot show notifications" and some second
wording of it end up disagreeing.

**What would reverse this.** A user reporting the banner as noise would mean one
of the two states is firing when nothing is wrong, and the fix is that state's
detection rather than the banner. If `backgroundWorkState` ever learns to return
`Blocked` from something measured rather than inferred, `ActionNeeded` gains a
third cause and the banner gains it too, with no change here.

## D-041. The widget asks for the size it has, and may be dragged to any size

**Decision.** `SizeMode.Exact` replaces `SizeMode.Responsive`, so `LocalSize`
reports the widget's real dimensions instead of the nearest declared one. The two
`DpSize` breakpoints go with it. `maxResizeWidth` and `maxResizeHeight` are
removed, `minResizeHeight` drops to 140dp and `minResizeWidth` to 250dp.

**Row count remains the only thing size may affect.** That rule survives intact
and is the reason the original choice was made; see below.

**Why. D-036's arithmetic was correct and was never given a real number.** That
entry replaced a breakpoint table with `available / RowHeight` on the argument
that "a widget is resized by dragging, so most real ones are neither declared
size". Under `SizeMode.Responsive`, `LocalSize` returns the matched member of the
declared set rather than the widget's actual size, so `heightDp` could only ever
be 190 or 266. The division was real; its input was quantised to the two values
the entry had just finished arguing against.

The visible cost, reported from a phone: a widget with room for five rows drew
three and left the rest blank. At 266dp the model computes
`(266 - 60 - 8 - 20) / 48 = 3`, and it computes that no matter how tall the
widget really is.

**`FocuslistWidgetModelTest` could not have caught it.** Its eight pinned cases
are the two declared sizes crossed with lead and text scale, which is exactly the
assumption that was wrong. A test that fixes the same two heights the production
code was stuck at will pass forever. Cases at heights no breakpoint would ever
produce are added with this entry.

**What this supersedes, and the part of it that was right.**
`docs/design/widget.md` argued: "Every launcher lets the user drag a corner, so
the two drawn sizes are two samples of a continuum rather than an enumeration.
Glance's `SizeMode.Responsive` takes a set of breakpoints and picks the largest
that fits... `SizeMode.Exact` would invite per-size layouts and is the wrong tool
here."

The first sentence is right and argues for the opposite conclusion: it names the
continuum and then picks the API that enumerates it. The likely slip is that
"picks the largest that fits" is true of what the launcher draws and not of what
the composable is told.

The second sentence is a real concern and it is kept rather than dismissed.
Handing code the true dimensions does tempt it to branch and grow a second
layout, which is the drift D-031 exists to prevent. But that is a worry about
what is done with an accurate number, not a reason to supply an inaccurate one.
The guard is that there is one layout and one reader: **any use of `LocalSize`
beyond `rowCapacity` is the thing to refuse.**

**The resize ceiling was never a decision.** `maxResizeWidth="364dp"` and
`maxResizeHeight="266dp"` are `MediumSize` to the pixel: the largest frame the
board happened to draw became the largest size the widget was permitted to be.
Nothing in any document argues for a maximum. With `minResize` equal to `min`,
the whole resizable band was 76dp in each axis, about one grid cell, so a
launcher offered resize handles that could not move. Advertising
`resizeMode="horizontal|vertical"` and then refusing to resize is worse than
either honest answer.

**Checked against TickTick**, on the emulator, the same way D-030 was. Their
scrolling task list declares `minWidth` 294 and `minHeight` 180 while allowing
`minResize` 110x110, and declares no maximum at all. A widget that shrinks to a
third of its placed size and grows without limit is the ordinary shape of this;
ours was the outlier.

**The new minimums are sized to the layout rather than copied.** 140dp is the
smallest height that still shows a task: 60 header, 20 disclosure and 8 bottom
inset leave 52 for one 48dp row. Below it the widget would draw furniture over
nothing. 250dp is deliberately conservative on width, because a row spends 48 on
the checkbox and 70 on the duration column before the title gets anything, and
nobody has looked at a narrow one on a device. TickTick reaches 110 with simpler
rows. Taking ours lower wants a render, not an argument.

**What was not adopted.** TickTick's list is a `RemoteViewsService` collection,
a scrolling `ListView` fed by an adapter, so the platform fills the height and
the remainder scrolls. It never measures, which makes blank space impossible and
`+N more` unnecessary. That is a sound design and it is not this one: D-031
allows the widget one count, "disclosing rows that did not fit", and a scrolling
home-screen surface cuts against the restraint that entry is built on. Catimo
keeps the measured design and fixes the measurement.

**What would reverse this.** A launcher reporting a size the widget does not get,
which would show as clipped rows rather than as empty space. That is the same
failure D-036 named, and it is now reachable for the first time, because the
number is finally the launcher's rather than one of two constants.

## D-042. OnePlus reminders take the alarm-clock path

**Decision.** On a device whose manufacturer is OnePlus, a reminder that has
exact-alarm access is scheduled with `AlarmManager.setAlarmClock`. Every other
device keeps `setExactAndAllowWhileIdle`, and every device still falls back to
`setAndAllowWhileIdle` when exact access is unavailable. The built-in test
reminder follows the same branch as a real one.

This is a delivery workaround, not the full-screen alarm D-038 deferred. It
adds no lock-screen activity, per-task setting, permission, or second delivery
surface. The notification remains the thing that interrupts the user.

**Why D-009's measurement is no longer enough.** That entry measured this
OnePlus 8T turning `setExactAndAllowWhileIdle` into an inexact alarm with a
window 0.75 times its futurity, and accepted roughly fifty seconds of drift as
usable for a task reminder. The phone then produced the product failure rather
than only the probe: reminders selected on minute boundaries posted at
23:07:04.924, 23:11:59.973 and 23:17:28.510, about five seconds, two minutes and
two and a half minutes late. `PRODUCT.md` says a reminder fires at the time it
was set for. Two minutes is not that time.

The surrounding checks were green: notifications granted, `USE_EXACT_ALARM`
granted, `canScheduleExactAlarms()` true, app standby bucket `active`, and the
app on the device-idle allowlist. That rules out the permission, standby, Doze
allowlisting, timezone conversion and the notification channel. It leaves the
same OxygenOS demotion D-009 saw in `dumpsys alarm`.

**The replacement was measured on the failing phone.** A five-minute probe
through the production `AndroidReminderAlarms` path produced:

    type=RTC_WAKEUP window=0 flags=0x9
    whenElapsed=+4m47s163ms maxWhenElapsed=+4m47s163ms
    Alarm clock: triggerTime=2026-09-09 23:35:38.285

There is no delivery window and the earliest and latest trigger are the same
instant. Android documents `setAlarmClock` as the most critical exact-alarm
path: it leaves low-power modes if necessary and never adjusts the delivery
time. That is the guarantee this product already makes.

The end-to-end path agrees. A built-in test reminder targeted
23:37:49.238 and its notification was posted at elapsed time 178822568 against
the alarm's 178821090 target: 1,478 milliseconds later, including the receiver's
work and the notification-service handoff. That is ordinary dispatch overhead
rather than the multi-minute delivery window reported against the old path.

**The cost is visible and is why the branch is narrow.** Android exposes the
earliest task reminder as the device's next alarm, including in system surfaces
that show that value. Tapping that affordance opens Catimo rather than one
task, because several reminders can exist and the system supplies one shared
show intent. A false positive therefore changes the phone outside the app, so
OPPO and realme do not inherit the branch merely because they share ColorOS
ancestry. They have not been measured. Samsung and Xiaomi already produced
zero-width exact alarms under the ordinary call and need no workaround.

**What would reverse this.** A measured OnePlus release that gives
`setExactAndAllowWhileIdle` a zero window, or evidence that presenting task
reminders as the next system alarm costs more trust than the late delivery it
prevents. There is no public API for an app to inspect its scheduled window, so
until Android exposes one the boundary cannot be learned at runtime.

## D-043. The widget's task list scrolls, and stops measuring itself

**Decision.** The rows become a Glance `LazyColumn`, which is a `ListView` in
RemoteViews terms, so every outstanding task for today is in the widget and the
overflow scrolls. `+N more` is removed. `rowCapacity` and the composable heights
it divided are removed, along with the `heightDp` and `fontScale` inputs to
`focuslistWidgetModel`. Nothing reads `LocalSize` any more, so `sizeMode` becomes
`SizeMode.Single`.

The lead card is the list's first item rather than a pinned header, so it scrolls
with everything else.

**Why. The count was a dead end.** `+N more` told the user work existed and gave
them no way to reach it: the only route was opening the app, which is the thing
the widget exists to save. D-041 made the widget fill its space and that helped,
but it cannot solve this. A full day overflows any widget, and the smaller the
widget the sooner. The disclosure was honest about hiding something and that is
not the same as being useful.

**What this supersedes in D-031, and what it does not.** D-031 allows the widget
one count, "disclosing rows that did not fit", and that clause goes with the
thing it described. The argument underneath it does not.

That argument is that a surface nobody asked to look at needs a higher bar to
speak up than a screen deliberately opened. It governs what the widget
**asserts** unprompted: the lead card still speaks only for a paused session or a
passed reminder, the end states are still two quiet lines, and the resting view
is unchanged. Scrolling is not an assertion. It is the user deciding to look
further, which is exactly the deliberate act D-031 contrasts a widget against.
Restraint about what a surface says is not the same as refusing to show more when
asked.

**D-036 and D-041 are not reversed, they are made unnecessary.** Both were
corrections to arithmetic that only had to exist because the widget was deciding
how many rows to draw. D-036 replaced a breakpoint table with division; D-041
discovered the division had been fed one of two constants for its whole life. The
honest reading of that sequence is that measuring was the liability: two entries
and three bugs went into computing a number the platform will work out for
itself if asked. `ListView` fills the height and stops. Nothing to keep in step
with the composables, and the obligation `FocuslistWidgetModel` carried, that a
row changing height without changing `RowHeight` silently breaks the
calculation, goes with it.

**`SizeMode.Single` supersedes D-041's `SizeMode.Exact`, one entry later.** That
is not a retraction of D-041's finding, which was correct and is what exposed
this. With no reader of the size, `Exact` would rebuild RemoteViews on every
resize to produce a layout that does not depend on the result. `Single` builds
once and lets the views stretch. D-041's guard, that `rowCapacity` is the only
reader of the size, is satisfied in the strongest available way: there is no
reader and no `rowCapacity`.

**The lead scrolls rather than pinning.** Pinning it would reserve 94dp of every
widget for a card that is absent most of the time, and it was the pinned lead
that produced D-036's original bug, a Compact widget with a lead card and no room
for a single row. As the first item it costs nothing when it is not there, and a
user who has scrolled past it is browsing rather than glancing.

**Precedent.** TickTick's task list is a `RemoteViewsService` collection, checked
by pulling its APK on the emulator. D-041 recorded that as the road not taken;
this takes it. Their widget never measures and has no equivalent of `+N more`.

**What this costs.** Row order is now the only thing telling the user what to do
first, over a list that may be longer than the screen. Today's bands are not
reproduced in the widget and are not going to be. If the widget starts reading as
an undifferentiated list, the answer is fewer rows rather than a returning count.

**What would reverse this.** A launcher that renders the collection badly, or
evidence that scrolling on a home screen goes unused, which would show as users
reporting they did not know the rest was there. `+N more` would then come back as
a footer under a fixed list, and this entry is the one to supersede.

## D-044. A missed-reminder notice can be acknowledged on Today

**Decision.** The missed-reminder banner on Today has a dismiss action. Dismissal
acknowledges one delivery incident: Catimo stores that delivery's ID and hides
the matching banner on Today across process restarts. It does not delete or alter
the delivery record, so the incident remains visible in Reminder Health until
the existing seven-day concern window expires. A later missed delivery has a
different ID and shows the banner again.

Only `Missed` can be dismissed. An `ActionNeeded` banner means Android cannot
currently deliver reminders as promised and remains visible until its cause is
fixed. The rest of a missed banner still opens Reminder Health; its trailing
action dismisses it. If that missed incident had been masking a simultaneous
active failure in Reminder Health's priority order, dismissal reveals the
`ActionNeeded` banner rather than leaving Today silent.

**Why this supersedes one clause of D-040.** D-040 made the Today banner
non-dismissible so an unresolved reliability problem could not be hidden. That
treated two unlike states as one. A permission or system-setting failure is an
active condition and must stay visible. A missed delivery is an immutable past
incident: repeating it on the primary task screen for seven days after the user
has understood it adds interruption without adding safety. Reminder Health is
still the durable record and explains when the notice clears.

The acknowledgement is keyed to the delivery rather than a boolean or date.
That gives it the narrowest meaning available: "I have seen this incident."
It cannot accidentally suppress the next miss, and it does not change reminder
health calculations.

**What this costs.** Today no longer forces a previously acknowledged incident
back into view. A user can therefore dismiss the only prompt that points to
Reminder Health, although the screen remains available from Settings. Keeping
active delivery failures non-dismissible and resurfacing every new miss retains
the warnings that can still lead to action.

**What would reverse this.** Evidence that users dismiss missed incidents before
understanding them and consequently overlook repeated delivery failures. The
first response would be clearer action copy or placement, not suppressing newer
incidents with a broader acknowledgement.

## D-045. The widget reads its data as a stream, because a Glance session outlives one read

**Decision.** `FocuslistWidget.provideGlance` stops capturing a snapshot and
starts observing one. The tasks, the day, the stored Focus session and the
completion evidence become a single `Flow<WidgetSnapshot>`, seeded once so the
first frame is real, then collected inside `provideContent` with
`collectAsState`. The widget follows the data for as long as its session is
alive.

`WidgetSnapshot` and the flow that builds it move to `FocuslistWidgetModel.kt`,
which is the file that exists so this kind of decision can be covered by JVM
tests.

**Why. The widget was frozen for the first 45 seconds after every refresh, and
had been since it shipped.** Everything above `provideContent` runs once per
Glance session, not once per update. `runGlance` is a `channelFlow` that calls
`provideGlance` a single time, `AppWidgetSession` holds it in a `remember`, and
`provideContent` suspends and never returns. A session lives 45 seconds past its
first composition and takes five more for each event.

`updateAll()` does not re-enter any of that. It posts `UpdateGlanceState`, which
reassigns Glance's own state object and forces a recomposition. Recomposing
re-rendered a captured local, so it drew the same data again. The read never
happened twice.

So an update arriving while a session was open changed nothing, and an update
arriving after one had expired started a fresh session and looked perfect. That
is the whole of the bug, and it is why it read as intermittent.

**What it looked like.** Three complaints, one cause. Tasks added in the app did
not appear on the widget. Unchecking a row the user had just checked did nothing,
because the check had opened the session that the uncheck was then trapped
inside. A widget showing one task of four caught up the moment the user tapped
it, because the tap landed after that session had already died.

**Measured, not reasoned.** Two completions in the app eight seconds apart, one
session: the first reached the widget and the second did not, and the two home
screen captures were byte-identical. Waiting sixty seconds for the session to
expire and writing once more brought every missed change through at once.

**This is older than D-043.** `git log -L` puts the shape at `e4f493c`, the
widget's first commit. Scrolling did not cause it. Scrolling exposed it: a stale
list is visibly stale, where a stale list truncated to `+N more` looked like the
widget simply being small.

**Why not close the session before each update.** `GlanceAppWidget.close` would
force a re-read by destroying the thing that caches it. That treats the session
as the problem when the session is the framework working as designed, it races
with any update already in flight, and it pays a full worker start for every
change. Reading observable state inside a composition is what the composition is
for, and it is what Glance's own `AppWidgetSession` does.

**Precedent.** TickTick runs no session. Its widgets are classic
`AppWidgetProvider` and `RemoteViewsService` pairs, twenty of them on the
emulator, and a data change calls `notifyAppWidgetViewDataChanged`, which makes
the launcher ask the factory for rows and the factory query the database at that
moment. There is no cached snapshot to go stale because the read *is* the
refresh. Glance can have the same property by making the read observable, and
keeps the composable rows D-043 relies on.

**`keepWidgetInStepWithState` stays and keeps its job.** While a session is open
the flow now carries the change on its own and the collector's `updateAll` is a
cheap event. While no session is open, that `updateAll` is the only thing that
starts one, so nothing reaches the widget without it. Its `drop(1)` and the
reasoning behind it are untouched.

**What this costs.** A widget session now recomposes on every task write for as
long as it lives, where before it did the work once. The snapshot is
`distinctUntilChanged`, so identical states do not republish, but a busy minute
in the app will push more RemoteViews than it used to. That is the correct trade:
the alternative is being wrong quietly.

**What would reverse this.** Evidence of battery or jank cost from the extra
recompositions, which would be answered by debouncing the flow rather than by
capturing it again. Capturing it again is the bug.

---

## D-046. Focus draws the cat instead of the shape, and the clock moves to the status line

**Decision.** The Focus sheet's 180dp shape is replaced by the app's own mascot,
in two poses. `cat-sit-front` while the clock is stopped, `cat-nap` while it
runs. The remaining time leaves the shape and joins the status line at body
size, so the line reads as the clock, then the budget:

    Ready              sit    45:00 · 45 min focus     Complete · Start focus
    Running            nap    44:37 · 45 min focus     Complete · Pause
    Paused             sit    32:18 · 32 min left      Complete · Resume
    Estimate reached   nap    00:00 · Estimate reached Complete · +5 min
    Open-ended         nap    12:43 · No time limit    Complete · Pause
    Open-ended paused  sit    12:43 · No time limit    Complete · Resume

Two drawings cover six states, on the partition D-014 already drew.

**The action row is reordered too, and it had never been decided.** Complete
leads and the clock control trails, where the row had been built clock-first and
left that way. Complete now sits in the same place in all six states, instead of
trailing in five and leading in the one state that has no clock control for it
to trail. And the clock is the control pressed repeatedly inside a session while
Complete is pressed once at the end, so the repeated one goes where the thumb
already is, which is the argument `expressive-components.md` already makes for
the Start focus pill on Task Details. Emphasis still carries the ranking and is
unchanged: Complete stays tonal beside a filled control, which is also the
ordinary Material arrangement of the two.

**What this supersedes.** D-014's shape, entirely: the `Cookie4Sided` at rest,
the `Cookie12Sided` while running, and the morph between them. Also D-014's
placement of the readout inside the shape at Headline Small.
`expressive-motion.md`'s rule that the app permits exactly one shape morph now
permits none, because the one it allowed was this.

**Why the cat rather than the shape.** D-014 reduced the shape's job to a single
sentence: it says whether the clock is running. That is a job an animal does
better than a polygon. The app already has the animal, drawn in five poses, and
`EmptyStateMascot.kt` already resolves it against three colour roles so it
follows dynamic colour. Nothing new had to be invented; a sixth and seventh pose
were drawn on the board that the other five came from.

It is also the warmth the product has been missing without buying anything
`PRODUCT.md` principle 7 forbids. There is no gift, no currency, no shop, no
achievement and no session history. The cat settles when you start and gets up
when you stop, and that is the whole of it.

**Why the clock moves.** D-014 already made this argument and stopped one step
short: "the largest object on a screen built to stop clock-watching should not
be the clock." At Headline Small inside the shape the countdown was still the
second largest thing on the screen. On the status line at body size it is still
a real readout, which D-013 requires because three of the six states cannot be
told apart without it, and the task title is now unambiguously the largest
object here. That is `PRODUCT.md` principle 2 finally getting the whole way.

**The cat does not wake up when the estimate runs out.** Estimate reached naps
like every other running state. `focus.md` already says overrunning is ordinary
and that the screen does not call it failure; an animal that sat up at the
buzzer would say it was. Only the status line changes.

**The ceiling, which is the point of writing this down.** The cat gains no prop.
It gains no third Focus pose. It never appears anywhere a session is not open.
Every version of this idea fails the same way, by growing a wardrobe, and the
failure is not the first pose, it is the fourth. Adding to this set requires
superseding this paragraph and saying what changed.

**Two smaller things it settles.** `cat-sit-front` makes a fourth sitting cat,
and that is deliberate: the three shipped sits are empty-state poses on list
screens, where posture says why a list is empty. This pair is in a sheet, is not
an empty state, and is read against its own partner rather than against that
vocabulary. And `EmptyStateMascot.kt`'s note that a `mid` nose is a sitting-pose
exception was wrong: `cat-nap` is a lying pose whose nose is also lighter than
its eyes, so the rule follows how a pose was drawn rather than what posture it
holds.

**The board's "Ear shading" layer is the far ear, and both poses draw it as a
subpath of the body.** Found on a device, and found twice. A hairline showed
across one ear on the phone, which read as an artefact between two same-coloured
paths, since the ear layer and the body both bind to `primaryFixed` and the
board's few percent of luminance between them collapses to nothing in the app.
Dropping the layer removed the seam and the ear with it: rendering the body path
alone shows it stops short and leaves a notch, so that layer is geometry rather
than shade, whatever it is called.

Merging it into the body path fixes both. One `addPath` holding both subpaths
fills as a union under nonzero winding, so the ear is present and there is no
join left to antialias twice. The five empty-state poses still add theirs
separately and are untouched; whether they show the same seam is a question
about those poses.

**The lesson is about layer names.** A name exported from a board describes what
the designer was thinking, not what the path covers. Anything binding two layers
to the same colour role should be rendered and looked at before one of them is
called redundant.

**The crossfade is one gesture on one spec.** The alpha is an effect and the
scale is spatial, and `Motion.kt` is right that those are different kinds of
animation. They are not two animations here. `focus.md` already records the
lesson from the container transform it deleted: "two things animating on
separate specs arrive at separate times, and the fix is to make one a function
of the other rather than to tune both until they agree." Both halves run off one
value on `mascotSettle`, the default spatial spec.

Nothing translates. Both poses share a ground line and are scaled about their
own feet, so the floor under the cat does not move while the body compresses.
The prototype this came from moved each pose a few pixels; moving either one
breaks the anchor that makes the pair read as one animal rather than as two
drawings dissolving.

**What it costs.** The app's shape work stops being a thing the app does. That
was distinctive, and D-014 had already reduced it to a two-state toggle, but it
is a real loss and this is where it is recorded. `FocuslistDimensions.FocusShapeSize`
is gone with it, and the poses take the mascot scale, 192.72 x 178.87 and
202.55 x 119.13, rather than a 180dp box.

**Timing, and the rule this bends.** This is Focus work landing while Phase 4 is
still open, which is exactly the tripwire D-013 set: "if Phase 4 slips while
Focus grows, this was the wrong call." It was sequenced here deliberately and
for one reason: Phase 5 ships the Play listing, the Focus screen is in those
screenshots, and shipping the Cookie means reshooting them. The code is small
because `MascotImage` already carries the colour resolution, the theming and the
accessibility decision.

**What would reverse this.** The nap not reading as *running* at a glance. That
is the one thing no test can answer, because it is a question about how fast a
posture reads, and it needs a device. If it fails, the answer is to swap the
poses — alert while running, curled when idle — and accept that it is duller.

## D-047. The widget's list takes the height it needs, so the space below it belongs to the widget again

**Decision.** The rows' `LazyColumn` drops `defaultWeight()` and measures to its
content. The header stays a fixed 60dp, the list takes what it needs up to what
is left, and any remainder belongs to the root `Column`, which carries the
whole-widget click.

**Why. D-043 quietly broke a documented target.** `widget.md`'s interaction table
has said from the start that tapping anywhere on the widget that is not a
checkbox, a row, Resume or add opens Today. Reported as "pressing the bottom
empty space on the widget does not open the app", and that is exactly what had
happened.

A weighted `LazyColumn` is a `ListView` stretched to fill every pixel below the
header whether or not it has rows to put there, and `AbsListView.onTouchEvent`
returns true unconditionally while the view is enabled, regardless of whether
anything in it is clickable. It swallowed every touch in that region. The root's
click listener never saw them, and there was no row under the finger to carry a
fill-in intent either, so nothing fired at all.

Measured on a launcher with three tasks on the default widget: the list was
handed 638px and its rows ended after 420, leaving 218px, about a quarter of the
widget, inert. The 8dp strip below the list still worked, which is what made it
read as arbitrary rather than as a rule.

**What this changes about D-043, and what it does not.** D-043 chose
`defaultWeight` "because the header above is a fixed 60dp and the list takes what
is left". That reasoning was about not colliding with the header and it survives.
What it did not consider is that "what is left" and "what the list needs" are
different numbers, and that the difference is a live region belonging to a view
that eats touches. The collection, the scrolling and the absence of any capacity
arithmetic are untouched: the platform still decides how many rows fit, and when
the rows exceed the space the list is capped and scrolls exactly as before.

**This is not measuring, which is the thing D-043 removed.** No code reads a
size, computes a row count or keeps a copy of any composable's height. Dropping
the weight asks the platform for one more thing it already knows, on the same
measure pass it was already doing.

**Verified on the minSdk floor, because there was a real reason to doubt it.**
`GlanceRemoteViewsService` branches on `SDK_INT > S`: API 32 and up carry the
rows inside the RemoteViews, while 29, 30 and 31 fetch them from a bound
`RemoteViewsService` that fills the adapter asynchronously. A `wrap_content`
`ListView` measured before its adapter has anything in it is zero tall, and a
widget that renders as a bare header is the worst failure this product has.

An API 29 emulator was installed to answer it rather than guess. On Android 10,
with the service-backed adapter: a long list still caps at the available height
and scrolls, a short list measures to its rows, a freshly dropped widget renders
its rows within a second and a half, and the region that was inert now opens
Today. Checkboxes still complete without leaving the home screen. The same
checks pass on API 37.

**What this costs.** A short list now leaves visible empty background rather than
an invisible empty list, which is the same pixels either way but is now honestly
part of the widget rather than part of a list. If that reads as unfinished, the
answer is what the widget puts there, not giving the space back to a `ListView`
that will not use it.

**What would reverse this.** A launcher or API level where a content-height
`ListView` collapses or fails to scroll. The fallback is not the weight again but
gating on the same boundary Glance uses, content height above API 31 and weight
at or below it, which keeps the fix where the adapter is synchronous.

## D-048. The Focus now card becomes the paused session card, and stops promoting its task

**Decision.** Four changes, and the first forces the rest.

`FocusNowReason.ReminderPassed` goes. The card appears for a paused focus
session and for nothing else. `FocusNowReason` and `FocusNow` go with it: a rule
with one reason has no reason to state, so the query returns the paused task or
null instead of a task paired with a justification.

The card stops promoting. Its task stays in whichever band it belongs to, so
Today's call to `todaySections` loses `promotedTaskId`.

The card loses its checkbox, its reason line and its whole-card tap target. What
is left is a label, the title, and Resume.

The label carries what the reason line carried: `Paused · 15 min remaining`, or
`Paused` for a session with no estimate behind it. "Focus now" as a label goes,
along with the question it was answering.

The widget follows the rule and leads on a paused session only. It keeps its
checkbox, its tap target and its promotion, for a reason given below.

**Why the reason had to go once the promotion did.** D-035 cut `NoTimeToday`
because the card under it *was* the first row of the band immediately below,
lifted out and drawn at double size, explained by that band's own label.
`ReminderPassed` survived that argument on one technicality: the promotion. The
promoted task was not in the band below, because D-012 had taken it out, so the
card duplicated nothing.

Take the promotion away and the technicality goes with it. The card would name a
task sitting in Overdue directly beneath it, under a label that already says the
work is late, carrying a time the row's own metadata already prints. That is
D-035's argument arriving, thirteen entries later, at the reason D-035 spared.

**Why the promotion goes.** Every awkward thing about this card exists because of
it. The checkbox is there because the row's checkbox was taken away. The
whole-card tap target is there because D-012 found that the promoted task had
become unreachable, "not in a band, not in Upcoming, which holds later days, not
in Inbox, which holds undated work". The empty branch tests
`tasks.isEmpty() && focusNow == null` because a screen holding a card is not
empty. `todaySections` carries a parameter for it. Four accommodations for one
rule.

The rule was not paying for them. D-012 promoted so the task would be on Today
exactly once, and that was right for the card D-012 described, which was a task
drawn large with a checkbox on it. This is not that card. With no checkbox it
cannot be completed, and with no reason line it asserts nothing about priority; it
says one thing the row cannot say and offers one action the row cannot offer. A
control about a task and a row for that task are not the same task twice.

**What the card is for now.** It narrows from "what should I do now" to "you were
in the middle of something". The first is a question the bands already answer by
ordering, past, present, future, with the answer at the top of the screen, which
is D-035's own defence of removing a card rather than adding to one. The second is
the single fact on Today that no arrangement of rows can produce: no row can say
that work was started on this task and has fifteen minutes left in it. D-035 said
exactly that when it chose what to keep, and then stopped one reason short of the
conclusion.

**The remaining minutes stay, and they cost no line.** The card is a title and a
button, and the number survives inside the label because it is the number the
decision turns on. A forty-five minute task paused two thirds of the way through
has fifteen minutes left, and a card showing the estimate would overstate the work
threefold. The label line already existed to hold "Focus now", which is what
vacates it, so `today_focus_now_paused_remaining` moves up a line unchanged and
the card is shorter than before rather than longer.

**Why the widget keeps promoting when Today stops.** The reachability argument
retires on Today and does not retire there. `WidgetLeadCard` is still a row: it
draws the checkbox, it takes a tap through to task details, and it adds a Resume
button on the end. It *is* the task, so leaving it in the list as well would be
the same task twice on a surface where a row is scarce. Today's card stopped being
a row; the widget's was never only a card.

The shared rule is untouched, which is the thing D-035 unified and this entry
keeps unified. One rule, `ReminderPassed` gone from both readers, and the
`lead.reason == FocusNowReason.ResumePaused` branch in `FocuslistWidget.kt`
disappears because it is now always true.

**Superseded by D-049, written the same day.** The two paragraphs above are kept
as written because they were the reasoning at the time, and they are now wrong on
the outcome rather than on the argument. D-049 removed the widget's lead card
outright and gave the widget labelled bands instead, so there is no lead to promote
and no Resume button on the home screen. What survives of this entry on that
surface is only the deletion: the widget reads no clock and no Focus session, which
is what removing `ReminderPassed` made possible and what D-049 then completed.

The rule is still shared in the sense that mattered, because there is no longer a
second reader to disagree with. `pausedFocusTask` has one consumer, Today.

**A documented wart retires with the reason.** `TaskListViewModel`'s `focusNow`
flow carried a caveat saying that a reminder passing does not re-run the rule on
its own, because `currentDay.today` "emits on a date change and nothing finer", so
the card "appears the next time anything else emits, which in practice is the next
write or the next time the screen is opened". `ReminderPassed` was the only reason
that read a clock. A paused session is paused whatever the time, so the flow loses
the day trigger, the query loses its `now` parameter the way D-035 already removed
its `today`, and the card can no longer be late.

**The cost, taken deliberately.** The card gets rarer, and by more than D-035 cost
it. `ReminderPassed` fired for any user who sets reminder times and lets one pass,
which is a normal week; a paused session requires having started work inside the
app and then left it. D-035 already accepted that "a card seen rarely is a card
that is learned slowly" and named the consequence, that the card's button is
Today's only one-tap route into a session. That route now exists only after a
pause.

Accepted, and the compensation is deliberately not a card. Task rows already open
Focus directly and skip Ready, per `focus.md`, so the route exists on every task
and is merely not at the top of the screen. If Focus turns out to be
undiscoverable, the fix is an entry point that does not depend on state, not a card
that goes looking for something to say. That is written down here so the next
session reaches for the entry point rather than for the reason.

**Overdue gains nothing.** A count on its label, of the kind Completed carries,
was considered and cut. The band is already first, already labelled, and already
holds the rows themselves, so a count would make it the third thing on Today
counting lateness, after the band's own contents and the reminder health banner.
The reason for removing `ReminderPassed` is that Today says this twice, and the fix
for that is not a third phrasing.

**What this supersedes.** D-012's promotion clause and its "the card opens its
task" clause, which stand or fall together and fall together here. D-035's second
surviving reason, leaving that entry with one. Neither is contradicted on its
reasoning: D-012 was right about the card it described, and this finishes the
argument D-035 started.

Two design documents state behaviour that stops being true.
`docs/design/today-screen.md` lists "Tap Focus now card body | Opens task details"
in its interaction table, and that row goes. `docs/design/focus.md` lists the card
as an entry that "opens the sheet in **Ready** on the task the card names", which
is now wrong for the only path left: a paused session resumes on the button press,
because the user has already decided and already started.

**A consequence this entry did not foresee: Ready is now unreachable.** Found while
correcting `focus.md`, and recorded here rather than fixed, because it is a product
question.

D-013 lists Ready among Focus's six states: a task chosen, with no clock running.
`openFocus` is the only function that lands there, and after this entry nothing in
the app calls it. Both remaining entry points, a Today row and Start focus on Task
Details, call `beginFocus`, which starts the clock. The card's non-paused branch was
the last caller of `openFocus`, and removing `ReminderPassed` removed the branch.

`focus.md` argues that "a drawn state nothing can arrive at is a state that should
not exist", which makes this a decision to take rather than a gap to leave open:
either something lands on Ready again, or D-013's six states become five and the
state comes out. Not decided here, because this entry is about Today's card and
that question is about Focus.

Worth separating from it: `focus.md` also claimed Start focus on Task Details lands
on Ready, and that was already untrue before this entry. `TaskDetailsScreen` has
called `beginFocus` for as long as the call has been there. The document has been
corrected; the code was not touched.

**What would reverse this.** Users pausing a session and then not finding it
again, which would mean the card is too quiet rather than too rare. Or overdue work
sitting unactioned in a way it did not before, which would mean the card had been
carrying the Overdue band and the band needs the voice this entry declined to give
it. The first shows up as sessions abandoned mid-task, the second as tasks overdue
for days.

Restoring the reason is no longer an enum value and a branch, which is the real
price of collapsing the type: it would be the enum, the data class, and both call
sites coming back. That is the trade this entry makes on purpose, because a type
that can only express one thing should not be shaped like a choice.

## D-049. The widget is a compact Today, with bands and without Focus

**Decision.** The widget drops the lead card and everything behind it, and draws
Today's bands instead. Overdue, No time set and Later today, each with the label
Today uses, over rows in the order `todaySections` already produces. No Focus
surface of any kind: no promoted task, no reason line, no Resume.

Completed stays out. On Today it is a collapsed disclosure the user can open;
a widget has nothing cheap to open into, and finished work is the one thing a
glanceable surface never needs to carry. The just-completed row is unaffected and
still holds its place, because that is evidence of what the user did rather than
a list of what they have finished.

**Why. The lead card had already lost its subject.** D-048 removed
`ReminderPassed` from the Focus now card and deleted `FocusNow`,
`FocusNowReason` and `focusNow` with the ranking, leaving `pausedFocusTask`.
That took away both of the two reasons D-031 gave the widget: `ReminderPassed`
was cut outright, and `ResumePaused` is the one D-048 keeps for a screen the user
deliberately opened. Carrying it onto the home screen would have been a new
argument, not a surviving one, and nobody made it.

**What D-031 loses, and what survives.** Its lead-card bullet goes. Its argument
does not, and it is the reason Completed is excluded and the bands are labels
rather than counts: a surface crossed involuntarily has to earn the right to
assert, so the widget still shows work and never comments on it. Bands are not an
assertion. They are the order the rows were already in, named. The user had to
infer that order before and now does not.

**This reverses D-043's closing line**, which said Today's bands are not
reproduced in the widget and are not going to be, and offered fewer rows as the
answer if the list read as undifferentiated. That was written when the rows were
one run under a card that explained the top of it. With the card gone the run has
nothing explaining it at all, and the honest reading is that D-043 was defending
a layout the lead card justified rather than the bands themselves. Fewer rows was
never a real option: the widget scrolls precisely so it does not have to withhold
work.

**The completion evidence stops carrying an index.** It recorded the row's
position because completing a task moved it to the bottom of a flat list. With
bands the task is shown as though it were still outstanding, which returns it to
its own band in its own place without anyone remembering where that was.
`widgetCompletionIndex` and `WidgetCompletion.previousIndex` go.

**A band heading needs its own tap target.** The root carries "anywhere else
opens Today" and `AbsListView` eats every touch inside the collection before the
root sees it, so a heading is inert without one. Rows never showed this, having
targets of their own. It is the second thing lost to that rule after D-047, which
makes it a rule worth stating: anything drawn inside the list needs its own
action.

**The widget no longer reads the Focus session at all**, so `storedFocus` leaves
`WidgetSnapshot` and the focus-change flow leaves `widgetSnapshots`. D-045's
stream and D-047's content-height list are untouched, and one input is gone from
each. `WidgetLaunchCommand.ResumeFocus` goes with the Resume button.

**What this costs.** A reminder that fired and was swiped away no longer gets a
card on the home screen naming it. D-031 called that the durable backstop for
D-005's promise, and it is a real loss. What replaces it is weaker and not
nothing: the task is in the widget under a band that says the work is late, which
is the same thing D-048 concluded was enough on Today. If missed reminders start
going unnoticed, the answer is reminder health work, which the app already has,
rather than a card that was restating its neighbour.

**What would reverse this.** Evidence that the bands read as clutter at widget
size, where the answer is fewer bands rather than none: Overdue is the one
carrying the product's defining failure and would be the last to go.

## D-050. The widget's checkbox carries both colours, because the launcher picks and this process cannot

**Decision.** `WidgetCheckbox` stops resolving its colour roles to a single
`Color` and passes a `DayNightColorProvider` instead, built by resolving the role
against a day configuration and a night one. Glance then writes both
`ColorStateList`s into the RemoteViews and the launcher picks, which is what
every other colour in the widget already did.

**Why. The checkbox was the only thing in the widget that could not follow the
system.** Reported as invisible in light mode. It is invisible in whichever mode
the widget was not built in.

`ColorProvider.getColor(context)` collapses a day/night pair by reading
`context.resources.configuration.uiMode`. That context is the app process at
composition time; the RemoteViews are drawn later by the launcher, in whatever
mode the system is in then. Everything else Glance emits as a pair and defers.
The checkbox was handed a `FixedColorProvider`, which has no night variant, so
Glance wrote the same colour into both slots and the value froze at whatever the
app process happened to be when the Glance session last ran.

The two agree until the system changes mode without the widget being rebuilt,
which on a phone with scheduled dark mode is every sunrise. The container flips,
the text flips, and the checkbox keeps yesterday evening's colour: a pale outline
on a pale surface, or a dark one on a dark surface.

**What the original workaround was actually avoiding.**
`CheckedUncheckedColorProvider` requires that neither colour is a
`ResourceColorProvider`, and dynamic Glance roles are exactly that, so the roles
cannot be handed over as they arrive. Resolving to a `Color` satisfied the check.
It also threw away the day/night pair, which was not what the check was asking
for: the same class explicitly supports `DayNightColorProvider` and reads it with
`getColor(isNightMode)` rather than from a context. Two colours were always
allowed. Only the resource form was not.

**Resolving twice is the supported way to build that pair.** A configuration
copied from the real one with the night bits set, through
`createConfigurationContext`, which is the same technique Glance's own
`resolveCheckedColor` uses for this. Copied rather than blank, because a dynamic
colour resolves against the whole configuration and an empty one would lose the
density and locale the lookup needs.

**Measured both ways.** Widget built in dark, switched to light with nothing
touched: fixed, the outlines turn dark with the surface; unfixed, they stay pale
and vanish. Built in light and switched to dark: fixed, they turn pale; unfixed,
they stay dark navy on a dark navy container. The unfixed captures were taken by
putting the old two lines back and running the same toggle, so this entry
describes a bug that was seen rather than one that was reasoned about.

**What this costs.** Two extra resource lookups per checkbox composition, on a
configuration-corrected context that has to be created each time. Both happen on
the Glance session's own dispatcher during composition, not on any frame.

**What would reverse this.** Glance gaining a compound-button API that accepts
resource-backed providers, at which point the roles could be passed straight
through and neither this helper nor the double resolve would be needed.

## D-051. The widget draws before it reads, because a session that has not drawn can be timed out

**Decision.** `provideGlance` stops awaiting a first snapshot and calls
`provideContent` straight away. The snapshot flow is collected with a null
initial value, and the null is a state the widget draws: `WidgetBody.Loading`,
which is the header alone. The bands arrive by recomposition a beat later.

`WidgetBody.Sections` carries the day it was built for, so the content no longer
needs a date passed alongside a model that may not have one yet.

**Why. A widget that never drew was reported stuck on "loading" for hours, and
the loading layout is the launcher's, not ours.** `dumpsys appwidget` on the
device showed the instance with no `views=` line at all, next to other widgets
that had one. Nothing had ever been published to it.

WorkManager's own diagnostics showed why nothing ever would: the
`SessionWorker` for that widget had already run and returned SUCCEEDED. A Glance
session that times out is a successful worker, because `doWork` ends
`Result.success(TimeoutExitReason=true)`.

**The timer that expired is the five second one.** `runSession` starts the
45-second `initialTimeout` only after the first `processEmittableTree` succeeds.
Before that the only timer that can be running is the one `observeIdleEvents`
starts when the device signals idle, and that is `idleTimeout`, five seconds.
Until `provideContent` is reached, `AppWidgetSession` emits `IgnoreResult()`
every frame and publishes nothing, so the whole window before the first read
completes is a window in which the widget can be killed having drawn nothing.

**This is D-045's seeding, and the reasoning in that entry was wrong.** It said
the initial value existed so the first frame would be real, because an empty list
would draw "nothing scheduled" and correct itself. That weighed a flash against
nothing, and chose to risk nothing. The flash was never the alternative either:
"not read yet" and "nothing scheduled" are different states and only one of them
was ever going to be drawn for a frame.

D-045's substance stands. The data is still a stream collected inside the
composition, which is what fixed the widget being frozen for a session's life.
Only the blocking read in front of it goes.

**Why this was invisible until a real phone.** An emulator is plugged in, awake,
and never signals idle, so the five second timer never starts and a slow first
read merely delays the first paint. A phone that idles aggressively starts that
timer while the session is still opening a database. The same code paints every
time on one and never on the other.

**Nothing retried, and that is what made it permanent.** `updatePeriodMillis` is
zero by design, and the application observer fires only on a task or Focus write
and drops its first emission. One lost session leaves the widget on the
launcher's loading layout until the user happens to change a task. Publishing on
the first composition closes the window rather than adding a retry: there is no
longer a period during which the session has nothing to show.

**Measured, by making the read slow on purpose.** With a twenty second delay in
front of the snapshot flow and everything else unchanged, the first publish
lands at t+7.8s on the new shape and t+27.7s on the old one. Those 7.8 seconds
are the cold start; the old shape spent the whole twenty on top of it having
published nothing, and that is the window a five second idle timer takes.
Measured by polling `dumpsys appwidget` for the widget gaining a `views=` line,
which is what an actual publish looks like from outside the process.

**What this costs.** One extra publish per session, the header alone, before the
data lands. On a warm process that is one frame. The residual risk is that the
flow never emits at all, in which case the widget shows an empty header rather
than the launcher's spinner, which is a state the user can at least recognise as
this app.

**What would reverse this.** Evidence that the header-only frame is visible long
enough to read as a broken widget, which would be answered by giving `Loading`
something to say rather than by waiting for data before drawing.

## D-052. Quick Add gets its heading back, and the field stops carrying the name

**Decision.** "New task" moves out of the text field's `label` and becomes a
heading on the sheet, `titleLarge`, above the field. The field keeps its
placeholder, its supporting line and its marking, and loses nothing else.

**Why. The name was the noisiest thing on a field that already says four
things.** The field carries a placeholder teaching the date trick, a supporting
line naming the resolved day, the parsed run marked in `primary`, and a reminder
chip under it when there is a time. On top of that a floating label repeated
what the sheet was for. The label was the only one of the five carrying no
information about the task being typed.

**The field does not get smaller, it gets emptier.** A Material 3 filled
`TextField` is 56dp tall whether or not it has a label; the label simply takes
the top of that container and pushes the text down. Removing it leaves the
container exactly where it was and gives the typed text the row back, which is
the outcome asked for: same size, more room.

**This reverses a line in `expressive-components.md`**, and that is the point of
this entry. Its Quick Add section records "The sheet also lost its heading; the
FAB that opens it already says Add task." That was right about the FAB and wrong
about where the name would end up. The heading did not disappear when it was
removed, it moved into the field, where it cost a row of the input instead of a
line of the sheet and was drawn on every keystroke rather than once. A heading is
the cheaper place for it, and the argument that the FAB already names the sheet
never justified naming it twice inside the field.

**Why not remove the name altogether**, which was the alternative considered. It
would have been the smallest change and it leaves the field just as clean. It
also leaves the sheet with no name at all in any form, which costs a screen
reader user the one landmark on it, and costs a sighted user the confirmation
that the sheet they got is the one they asked for.

**The field is named by the heading rather than by a description.** Setting
`contentDescription` on an editable node risks replacing the announcement of
what the user has typed, which is worse than an unnamed field. A visible heading
marked with `heading()` immediately above a single field is the pattern this is
for, and it is what `RepeatSheet` and the Task Details sheets already do.

**What this costs.** A line of sheet height that the sheet did not spend before.
On a one-field sheet with a keyboard up, that line is affordable in a way a row
of the input was not.

**What would reverse this.** A second field ever arriving on this sheet, which
`expressive-components.md` forbids and D-011 reaffirmed. Two fields would need
their own labels and the heading would start competing with them.

## D-053. Quick Add's supporting line says where the capture lands, and says nothing before there is one

**Decision.** The sheet is told where an undated capture goes, and the line under
the field says it: "Saved to Today" from Today, "Saved to Inbox" from Inbox, and
the resolved date when the title named one. The line is drawn only once the
title would save something, so an empty field is silent.

The fact travels as the fallback date itself, `null` when the host leaves a
capture undated, because that is the same value the host acts on at save time.
A destination flag would have been a second copy of the answer and free to drift
from it, which is how this went wrong in the first place.

**Why. It was telling the truth on one screen and not the other.** Quick Add is
opened from two places that resolve a missing day differently. `TodayScreen`
saves `parsed.date ?: today`, so an undated capture gets today's date and lands
in Today. `InboxScreen` saves `parsed.date`, which is null, so the task keeps no
date and stays in Inbox: that is the decision Inbox exists to defer.

The line read neither. It looked only at `parsed.date`, found it null in both
cases, and said "Saved to Today" either way. Capture from Inbox without typing a
day and the sheet named a destination the task was not going to.

**This is a trust bug and it was ranked as one.** `CLAUDE.md` puts a missed
reminder above a crash because a crash is visible. The same ladder applies here:
a user told their task went to Today looks in Today, does not find it, and has
nothing to work out why from. It was fixed before the sheet's more annoying
problem, which is that the keyboard's Done key did not save, on those grounds.

**The empty field goes quiet for a different reason.** "Saved to Today" was
shown before anything had been typed, which asserts a destination for a task
that does not exist. The line is now tied to the same test the Add button uses,
so it appears exactly when there is something for it to describe, and the two
cannot disagree about whether a capture is real.

**The dated case still names the date, not the list.** A task scheduled for
Friday appears in Upcoming, so "Saved to Upcoming" would be true, and it would
be worse. `expressive-components.md` gives this line the job of naming the
resolved date, which is the thing the user typed and wants confirmed. The list
follows from the date; the date does not follow from the list.

That leaves the line naming a destination only when there is no date to name,
which is also the only time the destination is the surprising part.

**What this costs.** One string, and a parameter every caller has to answer. The
parameter is the point: a new host cannot open this sheet without saying where
an undated capture goes, which is the question that was previously answered by
assumption.

**What would reverse this.** Inbox gaining a default date, which D-003 and the
Inbox design both refuse, or a third host with a destination that is neither.

## D-054. Ready comes out, and Focus has five states

**Decision.** `FocusState.Ready` is removed, and D-013's six states become five:
Running, Paused, EstimateReached, OpenEnded, OpenEndedPaused. The Start focus
control goes with it, because Ready was the only state that offered one, and so
does `TaskListViewModel.openFocus`, which was the only way to reach Ready.

`focusStateOf` and `focusReadout` stop taking a nullable session. Opening the
Focus sheet now implies a session exists, and the sheet returns early on a null
one the way it already does on a null task.

**Why now, and what makes it safe.** D-048 removed the Focus now card's second
reason, and with it the card's non-paused branch, which was `openFocus`'s only
caller. Nothing else has ever called it: a Today row and Start focus on Task
Details both call `beginFocus`, which starts the clock. So the state has been
drawn, tested and unreachable since D-048 landed.

The check that matters is whether a null session can reach the sheet by some
other route, because that is what Ready was rendering. It cannot.
`_isFocusSheetOpen` starts `false` and is not persisted, so a process restart
closes the sheet rather than restoring it onto a session that may be gone. The
three paths that open it all guarantee a session first: `beginFocus` starts one,
`resumeFocusFromCard` resumes one that already exists, and `openFocus` was the
only path that deliberately nulled it. With it gone, sheet-open implies
session-exists, and the nullable parameters were expressing a state the app can
no longer be in.

**`focus.md` asked for this in its own words.** It says "a drawn state nothing
can arrive at is a state that should not exist", written when Ready was
*reachable* and the document was explaining why the card had to reach it. The
sentence outlived the entry point. Taking the state out is the document's own
rule applied to the situation D-048 left.

**Why not the other answer.** The alternative was to give Ready an entry point
back: land something on it so the state earns its place. That was weighed and
refused, because nothing in the product wants it. D-012 built Ready for a card
that had chosen a task on the user's behalf, where Ready was the user agreeing
with the app's pick before the clock ran. D-048 removed the app's picking. Every
remaining route into Focus is one the user chose themselves, and `focus.md` has
said since D-023 that "picking one task out of a list and choosing Focus on it is
the deciding already done". A state whose whole purpose is confirming a decision
the user has already made is friction, and adding an entry point to justify
keeping it would be building the product around the code.

**What this costs.** A session can no longer be opened stopped. Every entry
starts or resumes a clock, so a user who wants to look at the Focus screen
without running time down has no way to do it. That is a real capability being
removed rather than dead code being swept, and it is the reason this is an entry
rather than a cleanup.

Taken deliberately, because the capability had no route to it for a user to miss.
If it is wanted back it should return as a designed entry point with a reason,
not as a state restored because its enum value was still there.

**What this supersedes.** D-013's state list, which is six and becomes five, and
its table row `Ready 45:00 45 min focus Start focus · Complete`. Nothing else in
D-013 changes: pause, resume, the extension and both open-ended states are
untouched, and they are what that entry was actually for.

`focus.md` needs its state count and its Ready sections cut, including the
paragraph beginning "Ready comes back too", which D-048 had already amended once
to say the route was gone.

**What would reverse this.** A user asking to open Focus on a task without
starting the clock, which is the capability named above. The fix then is an entry
point that lands there and a reason written down for it, and the state itself is
a `when` branch and an enum value: the expensive part of this entry is the
argument, not the code.

## D-055. The app is called Catimo, and only the parts a user reads were renamed

**Decision.** The product is Catimo. Every string a user can read says so: the
launcher label, the notification header, the widget picker, the backup copy, the
reminder permission body, the reminder health screen and the manufacturer
restriction lines. The exported file the system picker offers is
`Catimo-backup-<date>.json`.

Three things deliberately keep the old name, and each would cost something real
to change:

- **The package and the `applicationId`, `com.vignesh.focuslist`.** Android
  identifies an app by this, not by its label. Changing it does not rename the
  app, it publishes a second one: no upgrade path, and anyone holding the first
  keeps it, along with their tasks. A rename in the UI is free; this is a
  migration, and there is nothing to gain from it.
- **The backup format marker, `focuslist-backup`.** It is written inside every
  backup file that already exists, and `FocuslistBackupCodec` refuses a file
  whose marker it does not recognise. Renaming it would make every backup a user
  already holds unopenable, which is the failure Phase 5's exit criterion is
  written to catch.
- **Class and file names**, `FocuslistTheme`, `FocuslistWidget`,
  `FocuslistApplication`, `focuslist.db`, `Theme.Focuslist`. These are internal,
  no user sees them, and renaming them would be a very large diff across every
  file in the project for no behaviour. Worth doing one day for tidiness, worth
  nothing today.

**The compatibility claim was tested, not reasoned.** A backup written by the app
before the rename, `Focuslist-backup-2026-09-09.json`, was restored onto a wiped
install afterwards and opened cleanly, swapping the task list to its own older
snapshot. The same session verified the round trip in the other direction:
export, `pm clear`, restore, export again, and the two files matched on format,
version, settings and all fourteen task ids with no row differing on any field.

**The documentation was renamed with the app, including the history.** Every
`.md` in the project now says Catimo in prose. The entries above this one were
written when the product was called Focuslist, and renaming the product inside
them changes no argument any of them makes: "Focuslist has no account, no sync
and no backend" and the same sentence with Catimo in it are the same claim about
the same software. What was left alone is anything naming a thing that did not
move, which in the documents means code identifiers in backticks and the Figma
board frames, `Focuslist — Components` and its siblings, which are still called
that in Figma.

So a reader finding "Focuslist" in this repository is looking at one of four
things: a package path, the backup marker, a code identifier, or a Figma board.
None of them is the product's name.

The design documents tag every rule as `[M3]`, `[FD]` or `[IMPL]`, and the middle
one stood for the old name whether or not anything spelled it out. It is `[CD]`
now, 227 of them, and the legend says what all three mean so the question does
not come back.

**What this does not decide.** Whether the Figma boards get renamed, whether the
package is ever migrated, and what the Play listing says. The listing copy is
Phase 5 work and is drafted from `PRODUCT.md`, which is why the documents were
renamed before it rather than after.

**What would reverse this.** Nothing about the name itself. The one thing worth
watching is the split: if the package name showing through somewhere a user can
see it turns out to be confusing, that is a bug in the surface that leaked it,
not an argument for the migration.

---

## D-056. Restore confirms before it replaces

**Decision.** Choosing a file on the Backup page no longer restores it. The file
is parsed first, and a Material alert dialog says what is about to happen in
numbers the user can check:

    Restore this backup?

    This backup holds 47 tasks. Restoring replaces the 12 tasks on this
    device, and cannot be undone.

    [Cancel]  [Restore]

Only the Restore button writes. Cancel leaves the database untouched and drops
the parsed file.

**What this supersedes.** `settings.md`'s "No confirmation step before a
restore. The tonal button already carries that weight, per the ordering above. A
destructive action that announces itself afterwards and a weaker button before
it is the whole of the protection here."

**Why that reasoning does not hold.** It is the right rule applied to the wrong
action. `AGENTS.md` says to minimise confirmation dialogs and to "provide undo
for reversible destructive actions where appropriate", and those two clauses go
together: the reason a delete needs no dialog is that a delete can be undone.
This cannot. `BackupDao.replaceTasks` deletes every row and every delivery
record inside one transaction, and nothing in the app puts them back.

So the protection settings.md described was a button weight and a message that
arrives after the loss. Between the tap and the write there was one system file
picker, and a file picker is not a decision about this app: a user who opens the
wrong `.json`, or the right filename from three months ago, has already
committed by the time they find out. "Restored 47 tasks" is then a report of the
damage rather than a check on it.

**Why numbers rather than a warning.** "This will replace your data" is a claim
the user cannot weigh. Two counts can be weighed against each other, and the
mismatch is exactly what identifies the mistake this dialog exists to catch: a
user restoring a stale file sees a smaller number than the one on their phone.
This is the same standard the success snackbar is held to and for the same
reason, that an assertion the user cannot check is one they cannot disagree
with.

The counts exclude soft-deleted rows on both sides, matching the snackbar. A
device with no tasks at all says so in a sentence instead, because "replaces the
0 tasks on this device" is a sentence no one writes.

**Parse first, then ask, then write.** The dialog cannot name a count it has not
read, so the parse moves ahead of it, and the parsed backup is held in the view
model until the user answers. That ordering was already half true:
`BackupRepository.restoreFrom` parsed and validated before the first local write
specifically so a damaged file left the task list alone. The change is that the
gap between parsing and writing is now where the user stands, rather than a
comment about transaction safety.

A damaged or foreign file therefore fails before the dialog appears, and still
raises the existing "Couldn't restore this file" error. That is the better
order: the user is never asked to confirm a restore that was never going to
work.

**What was considered and rejected.** An "Export current data first" action
inside the dialog, which the audit that produced this entry suggested. It is a
real protection and it is a second flow: dismissing the dialog, launching the
`CreateDocument` picker, holding the parsed backup across it, and coming back.
That wants designing rather than bolting onto a confirmation, and the dialog
without it already closes the finding.

**What would reverse this.** Users dismissing the dialog without reading it and
restoring the wrong file anyway, which would mean the counts are not being read
and the protection is theatre. The check is whether anyone ever cancels.

---

## D-057. Starting Focus on another task asks before it discards a session

**Decision.** `beginFocus` no longer always creates a new session. Three cases:

    no session at all              start one, open the sheet
    a session on this same task    resume it, open the sheet
    a session on another task      ask, and write nothing until answered

The question is a Material alert dialog over Task Details, naming both tasks:

    Switch focus?

    Draft the quarterly note has 12 minutes on its clock. Starting Book
    the dentist ends that session, and the time on it is not kept.

    [Cancel]  [Switch focus]

**Both names are in the body, and neither is in the title or on a button.** A
task title is user text of no fixed length. Material draws a dialog title in
`headlineSmall` and a button label on one line, and neither survives a sentence
someone typed into a capture field, whereas body text wraps. So the title says
what is being decided, the body says which two tasks and what it costs, and the
buttons stay short enough to read at a glance.

The elapsed time is in there because it is what actually decides the answer: two
minutes and forty minutes are different questions. Under a minute there is
nothing worth reporting and the sentence drops it rather than saying "0
minutes".

**Why this follows D-015 rather than reversing it.** D-015 ranked the two ways
of being wrong about a Focus control: "Stopping when the user meant to pause
loses the elapsed time and the sense of progress with it, silently, with nothing
that puts it back. Pausing when the user meant to stop leaves one card on Today
that they can ignore. One failure is unrecoverable and invisible; the other is
visible and costs a glance."

Starting Focus on a second task committed the unrecoverable one. `beginFocus`
called `restartFocusClock`, which writes `FocusSession(startedAt = Instant.now())`
over whatever was there, and `writeFocusSession` overwrites the saved state and
the `FocusSessionStore` with it. A paused forty-minute session went, silently,
with no undo and nothing on Today left pointing at it.

D-015 protected the exit from Focus and left the entrance open, and the entrance
is where the same loss actually happens. The principle is unchanged; it is being
applied to the second door.

**Why not simply refuse, or simply keep both.** Refusing would make the paused
session a trap, which is the objection D-015 raised against the running one.
Keeping both is a Focus queue, and D-004 cut that.

**Why a dialog here and not elsewhere.** `AGENTS.md` says to minimise
confirmation dialogs, and D-023 and D-054 both hold that picking a task and
choosing Focus on it is the deciding already done, so a screen that asks the
user to confirm it again is friction. That is still right, and it is about the
ordinary case: the dialog does not appear when there is no session, which is
almost every time anyone taps Start focus. It appears only when the tap would
destroy something, and only then is there a second thing for the user to know.

**Same task resumes rather than restarts, which was the quieter half of the same
bug.** Opening Task Details for the task you are already focusing on and tapping
Start focus reset its clock to zero. There is no reading of that tap under which
the user wanted their progress cleared, so it needs no dialog: it is the Resume
the paused card already offers, reached from a different screen.

**Cancel does not navigate.** It closes the dialog and leaves the user on Task
Details for the task they did not start. Landing them in Focus on a
different task, from a screen about this one, is a surprise, and the paused card
on Today is one tap away and is the route D-015 built for exactly this.

**A session whose task is gone is replaced without asking.** Completing or
deleting the focused task from outside the sheet leaves the pointer behind, and
there is nothing to preserve and no name to put in the dialog. The screen
resolves the current task before it asks, and switches straight through when it
cannot find one.

**What this does not fix.** There is still no way to end a paused session
without completing its task or replacing it. That is D-015's recorded cost and
it is unchanged here; this entry stops the replacement from being silent, it
does not add the missing control.

**What would reverse this.** The dialog appearing often enough to be noise,
which would mean users routinely run one session while starting another and the
real answer is different. The check is which button gets pressed.

---

## D-058. The health screen says whether a reminder has been seen to arrive

**Decision.** `ReminderHealthState.Ready` carries whether the app has actually
observed a reminder land on this device. The frame, the colour and D-021's
ordering are unchanged; the words are not:

    unverified   Not yet verified   Permissions are in place
                                    Catimo has not seen a reminder arrive
                                    on this device yet.

    verified     Ready              Reminders are arriving on time
                                    The last reminder Catimo sent arrived
                                    when it was due.

A recent delivery that was not concerning makes it verified. The recency window
is `ConcernWindow`, the same week the rest of the screen works in, so a device
that was fine a year ago is not still trading on it.

`No restrictions detected` becomes `No known restrictions detected` in the same
change.

**Why.** D-009 says the screen has to answer "is this device actually delivering
exact alarms" rather than "is this app allowed to ask for them", and it says a
screen built on permission checks alone "would report green on a device that
silently drops reminders". The screen was doing the second thing while claiming
the first. On a fresh install with three permissions granted and no reminder
ever sent, it said **Reminders are healthy** and **Exact reminders can currently
be delivered**, and the entire evidence for both sentences was
`canScheduleExactAlarms()` — the call `AGENTS.md` says outright is "not evidence
that an alarm will be exact", measured wrong on a real OnePlus in D-009.

That is the product's central claim, asserted on a check the project has already
written down as unreliable, on the one screen whose whole value is that the user
believes it.

**Why not a fifth headline state.** The audit that produced this entry asked for
a neutral "Not yet verified" state of its own, between Ready and Worth checking.
Refused, because it would be D-021 in reverse. D-021's argument is that a
reliability screen which is always red teaches people to ignore it; a screen
that almost never says Ready teaches the same lesson at the same speed, and most
users would sit in the new state indefinitely, since most people never send a
test reminder and a real one only arrives when they scheduled one. Adding a
severity level to an ordering built on certainty, for a difference that is not
about severity, would also make `state` wrong: not-yet-measured is not worse
than measured-fine, it is less known about the same good news.

So the distinction lives inside Ready, where it changes the sentence and nothing
else.

**Why any on-time delivery counts, and not an idle-exposed one.** The screen
already has a stricter test: `backgroundWorkState` requires `EvidenceOfHealth`
consecutive deliveries that each pass `testsIdleDelivery`, because clearing a
manufacturer warning is a claim about a phone that has been left alone. This
flag answers a smaller question, whether a reminder from this app has ever
arrived at all, and the copy is written to claim only that. Requiring idle
exposure here would mean the Test reminder button, which fires in thirty
seconds, could never move the screen off "Not yet verified", and a check the
user cannot satisfy from the screen it is on is a check that reads as broken.

The two live side by side on purpose: one says a reminder has been seen to
arrive, the other says whether this device delays the ones set far enough ahead
to be worth measuring.

**What stays true.** Verified is not a promise. A phone that delivered
punctually last Tuesday can miss tomorrow, which is why `Missed` still outranks
everything and why the window expires. The change is that the screen now
distinguishes what it was permitted from what it has seen, which is the
distinction D-009 exists to make.

**What would reverse this.** Users reading "Not yet verified" as a fault and
going looking for a problem that is not there. The check is whether anyone opens
a settings screen from that state.

---

## D-059. The task row shows a due date

**Decision.** A due date joins the row's metadata line, worded so it cannot be
read as the scheduled day:

    Due today          the deadline is today
    Due tomorrow       the deadline is tomorrow
    Due Sep 14         further out
    Overdue            the deadline has passed and the task is not done

It sits after the scheduled date and before the recurrence, so the line reads
reminder, then when it is planned, then when it is owed, then whether it comes
back. A past due date also colours the metadata line, through the same
`isOverdue` cue a past scheduled date already uses.

**This is a defect, not a scope change.** `task-row.md` has listed due date as
row metadata since it was written, and `PRODUCT.md` lists it as a field a task
may have. It was never built. Task Details could set a due date and nothing in
Today, Inbox, Upcoming or the Logbook ever showed it again, so a user could
record a deadline and never see it a second time.

**Why "Due" is on the front of every one of them.** The scheduled date renders
as "Today" and "Tomorrow" through the same `scheduledDateLabel` helper, and two
bare day names on one line cannot be told apart. The word is what makes the
second one a deadline rather than a repetition. The two are different things:
`PRODUCT.md` says a reminder is independent of a scheduled date and of a due
date, and the same holds between those two, since a task planned for Monday can
be owed on Friday.

**Overdue names the state rather than the day.** A date three weeks gone tells
the user nothing they need; that it has passed is the whole content, and the
exact day is one tap away in Task Details. This is also the only value on the
line that is not a date, which is what stops it reading as a scheduled day.

**Colour follows the existing rule and nothing moves.** `tertiary` on an overdue
date is already the app's cue, argued in `expressive-design-system.md` as a
second channel on top of a distinction that is already textual, and the words
here differ first. What does not change is which list the task is in: a due date
is not a scheduled date, and no band, section or query reads it. A task owed
yesterday and planned for Friday stays on Friday, coloured.

**The reminder is still suppressed only by a past scheduled date.** The row
hides its reminder time when the scheduled day has passed, because a reminder
that already fired describes nothing that is going to happen. A past *due* date
says nothing about whether the reminder has fired, so the two conditions are
kept apart rather than folded into one flag. Folding them would have hidden a
live reminder from a task that is merely late.

**A completed task shows its due date plainly.** The Logbook is a record of what
was finished, and colouring a deadline that was met, or missed, after the fact
is the app grading work that is already done. Overdue is a live state, so it
ends when the task does, and the row falls back to the day.

**What would reverse this.** Rows becoming crowded, which is the risk the design
doc names when it says metadata should stay compact. A due date is rare in
practice and mutually exclusive with nothing, so the line grows by one segment
on the tasks that have one. If that turns out to be too much, the answer is to
drop the scheduled date where a heading already carries it, not to hide the
deadline again.

---

## D-060. The paused session card can end its own session

**Decision.** The card gains a second action. Resume stays as it is, filled, and
`End session` sits beside it as a text button:

    Paused · 15 min remaining
    Refine landing page hero

    [Resume focus]  End session

`End session` calls the `endFocus` that already exists: no session, no chosen
task, no alarm. The task itself is untouched, and stays in whatever band it was
already in.

**What this supersedes.** D-015's "There is no longer a control that stops a
session outright", and D-048's "What is left is a label, the title, and Resume."

**Why, and what new information there is.** D-015 accepted this cost explicitly
and named the thing to watch: "A user who abandons a session leaves a paused one
in the Focus now card until they finish the task or start another. That is
clutter, and it is the price." It then wrote the reversal condition as "paused
sessions accumulating in the Focus now card and reading as nagging rather than as
a way back in."

That condition is now met by construction rather than by observation, and D-057
is what changed it. Before D-057 there were two exits from a stale session:
complete its task, or start Focus on another one, which silently overwrote it.
The second was a bug, and closing it removed an exit. A user who wants the card
gone and does not want to finish that task now has to answer a dialog and start a
session they did not want, on a task they did not choose, in order to clear a
card. That is worse than the clutter D-015 priced in.

**Why the card and not a Focus overflow.** The audit that raised this suggested
an overflow inside the Focus sheet. Refused, because the sheet is on the far side
of the problem: the only way in from a paused session is Resume, so ending one
would mean starting the clock you intend to discard, then finding a menu. The
card is the object that persists and nags, and a thing that will not go away
should carry the control that dismisses it.

It is also where the user already is. The card is on Today, above the first band,
in the place they are looking when they notice it is still there.

**Why no confirmation, when D-057 added one.** D-057's dialog exists because the
loss was a *side effect* of asking for something else. Here it is the action,
under a label that names it. The protection is the weight of the two buttons,
filled against text, which is the same argument `settings.md` makes for Export
and Restore.

**Why no undo, stated plainly.** What is lost is a clock reading. The task, its
notes, its date, its estimate and its reminder are all untouched, and the session
was already paused, so nothing in flight is interrupted. An undo would mean
teaching the undo mechanism a third kind of thing to hold, for a value that is
not the user's work. If people report ending sessions by accident, that is the
signal to reconsider.

**Resume leads and End session trails**, which is the opposite of D-037's
ordering and does not contradict it. D-037 put the destructive action first
because in a floating toolbar at the bottom of the screen the thumb lands nearest
the reaching side. This is a left-aligned action row near the top of the screen,
where that pressure does not apply, and the fill is doing the work instead.

**What this does not do.** It does not bring back a stop control inside Focus.
Leaving the sheet still pauses, D-015 is unchanged on that, and the whole of
D-015's argument about the close control not being able to tell two intentions
apart still holds. This adds a second, explicitly labelled control somewhere
else, which is exactly what D-015 said the alternative to overloading the first
one would be.

**What would reverse this.** Sessions being ended and immediately restarted,
which would mean the control is being read as a way to reset the clock rather
than as a way to stop. The fix then is naming, not removal.

---

## D-061. Today's second band is No reminder set

**Decision.** The band label reads `No reminder set`. `TodayBand.NO_TIME_SET`
becomes `TodayBand.NO_REMINDER` and the string key moves with it, so the code
says what the label says.

    Overdue            past, and needs a decision
    No reminder set    today, nothing will announce it
    Later today        today, it will announce itself
    Completed · N      a disclosure, collapsed by default

**What this supersedes.** D-012's choice of "No time set", and only that. Every
other part of D-012's band list stands.

**Why. The label was contradicted by the rows underneath it.** Tasks in this band
show their estimate at the end of the row: `45m`, `20m`, `15m`. A band headed
"No time set" holding three rows each displaying a time is a screen arguing with
itself, and the reader has no way to know that one means a time of day and the
other a duration. D-012 could not have seen this: the estimate moved into the
row's trailing slot afterwards, and the two changes were never looked at
together.

The name was also wrong about the code. `todayGroup` splits on
`task.reminderAt == null`, and D-012's own body says the bands "are told apart by
whether the task carries a reminder, which is what 'it will announce itself'
means". The label said time and the rule read reminder, so a task scheduled for
today at no particular hour and a task with a reminder were being described by a
word that does not distinguish them.

**Why not "Any time today", which is what the audit preferred.** Because D-012
answered that one and the answer has not changed. D-002 cut Anytime as GTD
vocabulary "that a broad Android audience does not arrive trained in", and the
word was then removed from the routes, the storage enums and the row menu.
Reintroducing it as a band label, for a third meaning, invites the confusion
D-002 was written to prevent. Nothing found since weakens that; the audit named
it as the calmer of two options without weighing D-002.

"No reminder set" meets all three of the tests D-012 set for the old name. It is
plain English. It says what the band contains. And it is the app's own wording,
more so than the label it replaces: "reminder" is the word `PRODUCT.md`, the
Reminder sheet and the Reminder health screen all use.

**The enum is renamed, and D-055's rule is why that is not inconsistent.** D-055
left `Focuslist` in class names on the grounds that no user sees them and the
diff buys no behaviour. The difference here is that `NO_TIME_SET` is not a stale
name, it is a wrong one: it asserts a rule the code does not implement, in the
one file where someone reading it would be deciding what the band means. Fifteen
sites across three source files and two test files.

**What is deliberately not renamed.** The entries above this one in this
document, which are history and say what was decided when. D-012 still reads "No
time set" and should.

**Rows still do not repeat the label.** D-012's rule that "a row reading 'No time
set' under a band reading 'No time set' is the label twice" is unaffected: no row
prints its own reminder state, and a row in this band has no reminder time to
print.

**What would reverse this.** Users reading "No reminder set" as an instruction to
set one, on a band whose whole point is that a task does not need one. The check
is whether tasks start acquiring reminders after landing there.

---

## D-062. A read that has not answered says so, and Task Details stops hanging

**Decision.** `TaskRead` gains `Loading`, and it is what `storedTasks` starts on
instead of `Loaded(emptyList())`. Task Details gains the three states it was
missing, drawn under an app bar with a back arrow in every one of them:

    read has not answered     the bar, and nothing under it
    read failed               the shared failed-read state, with Try again
    read answered, no task    "This task is no longer available", with Back

Nothing else changes. Every list derives from `read.tasks`, `Loading` carries an
empty one, and `readFailed` still asks `is Failed`, so all four lists behave
exactly as before.

**The bug.** Task Details is reached by id and has to decide whether a task it
cannot find is deleted or simply not read yet. It decided with this:

    task != null -> taskWasShown = true
    taskWasShown || tasks.isNotEmpty() -> onBack()

`tasks.isNotEmpty()` was standing in for "the read has happened", and on a device
holding no other tasks that proof never arrives: the list is empty before the
read and empty after it. So a notification pointing at a deleted task fell
through both branches and `val current = task ?: return` drew nothing. No app
bar, no message, nothing to tap. It stayed that way.

Reachable two ways worth naming. Completing or deleting your last task from its
own notification, then tapping the notification again before it clears. And a
restore, which is the more likely one now that the picker keeps a file list: the
notifications for the replaced tasks outlive the tasks, and D-056's dialog makes
restoring a stale file a thing users will now do deliberately and survive.

**A failed read hit the same dead end**, and nobody had noticed, because D-034's
error state was only ever wired to the lists. A read that throws leaves `tasks`
empty, which is the same trap by the same route. Task Details now draws the same
state every list draws, from the same strings and the same Try again, which is
what D-034 meant by one read failing once.

**Why the fix is a state on the flow rather than a smarter guess.** The screen
was inferring a lifecycle fact from a data fact, and there was no version of that
inference that could work: an empty list is genuinely ambiguous and the only
thing that can resolve it is the flow saying which it is. `Loading` is one object
and one `initialValue`. Everything downstream that only wants rows keeps reading
`read.tasks` and never learns the state exists.

**Why it does not navigate away on its own, which is where this departs from the
audit.** The audit asked for the message and then a return to the originating
list. Refused, because the two fight: a user who tapped a notification would see
a screen appear and vanish, and land on Today with no idea what happened. There
is also no originating list to return to — the deep link's back stack is whatever
the app was already showing. So the screen says its piece and Back is one tap,
which the bar and the button both offer.

**A task that was on screen and then went still leaves silently**, and that is
unchanged. The user completed or deleted it, the list they land on carries the
undo offer, and an explanation would be the app narrating their own tap back to
them. The two cases are told apart by `taskWasShown`, which the screen already
tracked.

**Nothing drawn while the read is in flight**, rather than a spinner. It resolves
in a frame from a local database, and `AGENTS.md` asks for motion that
communicates a state change. What matters is that the app bar is there, so even a
read that never returns is not a trap.

**And the failed-read state had never been reachable, which this found.** D-034
says "every view derives from the caught stream rather than from the repository
directly", and three did not: `focusedTask`, `pausedFocusTask` and the Focus
sheet's gone-task watcher each subscribed to `repository.observeTasks()`. Their
comments say why, in as many words — the caught stream "starts on a placeholder
and reading the placeholder as gone would close the sheet on the way in" — which
was true of `Loaded(emptyList())` and is exactly the ambiguity `Loading` removes.

A `Flow` that throws is finished, and those three had no `catch` between them and
Room. So a failed read did not draw the error state D-034 built. It crashed the
app on Today, before anything could be drawn. The state was unreachable by the
one failure it exists for, and nothing caught that because nothing tested a
failed read at all until this entry did.

They read `loadedTasks` now, which is the caught stream with `Loading` and
`Failed` filtered out. Filtering `Failed` rather than passing its empty list is
the load-bearing half: an empty list here reads as "the task is gone" and would
end a focus session because storage hiccuped. Not emitting leaves each watcher
holding what it last knew, which is the truthful answer when the app cannot see.

**What this does not do.** Six one-shot reads on the write paths still call
`repository.observeTasks().first()` inside a `launch`, and would still throw on a
failed read — `toggleComplete` and its siblings, reached only by a deliberate tap
on a device whose storage is already broken. Routing them through the caught
stream would swap a crash for a silent no-op, and choosing between that, a
message and a retry is a design question rather than this defect. Filed, not
fixed.

The four lists still start on an empty loaded list and still flash a false empty
state on a slow read. They now have the flag that would
fix it, `tasksLoaded`, and using it is a different question: a list needs to
decide between a skeleton, a spinner after a threshold, and nothing, and that is
a design call rather than this defect. Left as the audit filed it.

**What would reverse this.** "This task is no longer available" appearing for
tasks that do exist, which would mean the read answers `Loaded` before it is
really settled. The check is whether anyone sees it after an ordinary tap from a
list.

---

## D-063. Completing a task is one write, and a write that fails says so

**Decision.** Three changes to the write path, in the order they matter.

**Completion is atomic.** `TaskDao.completeWithNext` marks the task complete and
inserts its next occurrence inside one `@Transaction`, or does neither.
`reopenWithoutSpawn` is its mirror. `TaskCompletion` calls those instead of
making two unrelated calls.

**Writes read their task with a point query.** `TaskDao.findTask(id)` replaces
`observeTasks().first()` at all seven sites, six in `TaskListViewModel` and one
in `TaskCompletion`. `findSpawnsOf(parentId)` replaces the in-memory scan that
reopening used.

**A write that fails is reported.** Every write in the view model goes through
one private `write { }` that catches and raises `WriteFailure`, which the
snackbar effect every list already hosts announces as "Couldn't save that
change".

**Why the transaction, and why it is first.** `TaskCompletion.complete` was:

    repository.update(task.copy(completedAt = clock()))
    next?.let { repository.insert(it) }

Two writes, nothing binding them. A failure on the second, or the process dying
between them, leaves a recurring task complete with no successor. Its reminder
is gone and nothing anywhere says so. `PRODUCT.md` is explicit — "completing one
occurrence must produce the next one, it must never make the task disappear" —
and principle 1 ranks a reminder that does not fire above a crash, because a
crash is visible and this is not.

That promise cannot be made by two writes that can half happen, and no amount of
error handling above them fixes it: by the time the second write fails the first
has already landed. `reopen` had the same shape and lost the occurrence in the
other direction, deleting an untouched spawn and then failing to clear the
completion.

**Why the point query.** Every write path reached its task by subscribing to a
stream of *every* task, taking one emission, cancelling it, and searching the
list in memory. That reads the whole table to find one row, and it has no error
path: a `Flow` that throws kills its collector, which is how D-062 found the app
crashing, and routing it through the caught stream instead would have suspended
for ever and made the tap silently do nothing.

A suspending point query has neither problem. It throws at one call site the
caller can wrap, and it cannot hang. The `deletedAt IS NULL` filter matches
`observeTasks`, so callers see exactly the rows they saw before.

**Why the failure is announced rather than swallowed.** D-062 left this open,
between a crash and a silent no-op, and the answer is neither. A user who ticks a
task off and is not told the write failed believes it is done — the app quietly
asserting something untrue about their work, which is the fault D-034 exists to
prevent, arriving through a different door. `AGENTS.md` already forbids the
silent version for scheduling failures.

No retry action on the snackbar. There is nothing to retry automatically, the
user's next tap is the retry, and a button that will fail again is worse than no
button, which is the same reasoning `settings.md` gives for the restore error
offering Choose another file rather than Retry.

**`WriteFailure` carries an id**, for the reason `BackupDone` does. Two failures
in a row are otherwise `equals`, the state does not change, the screen never
notices the second, and the user taps into silence.

**The whole read-decide-write unit is inside the `try`,** not just the read. If
the database cannot be read it usually cannot be written either, and guarding one
half would leave the other crashing for the same reason.

**What is not claimed.** The rollback guarantee is Room's, declared by
`@Transaction` and not asserted by a test here: there is no way to make one of
these two writes fail from outside without testing Room rather than this code.
What is tested is that both writes land, that a refused write is reported instead
of fatal, and that a second refusal is announced too.

**What would reverse this.** "Couldn't save that change" appearing in ordinary
use, which would mean writes are failing for a reason worth finding rather than
because storage is broken.

---

## D-064. Start focus gets its word back, and Delete does not

**Decision.** The Task Details toolbar loses its attached FAB. Start focus
becomes a filled `Button` carrying the play glyph and the words "Start focus",
at the trailing end of the toolbar's own content row. Delete stays exactly as it
is: a bare icon button in `error`, leading.

**What this supersedes.** D-037's choice of the attached FAB, and the cost it
accepted with it. D-037 named that cost and named its own reversal condition:
"a first-time user sees two glyphs rather than a worded action. That is the trade
this decision makes, and if it turns out to cost more than the overflow did, the
entry to supersede is this one."

**Why it cost more.** D-037 argued the glyph was safe because the trap it was
solving had gone: a filled pill without a glyph "was pressed by people meaning to
close the page", and "a small play icon in a floating bar is not open to that
reading". True, and it answers the wrong question. The icon is no longer
mistakable for Close; it is mistakable for everything else. A play triangle on a
screen about one task reads as preview, as resume, as run. Focus is none of
those, and it is the screen's payoff — the one control the whole page exists to
lead to.

The comparison D-037 set up also does not hold in its favour. An overflow with
one item promised options it did not have, which is a small lie about the shape
of the menu. An unlabelled play button makes no promise at all, which is worse
for the action that matters most here.

**Why Delete keeps no word, which is the part worth arguing.** The audit that
raised this proposed labelling Delete too, or moving it back to an overflow.
Both refused.

A trash can is not ambiguous the way a play triangle is. It is among the few
genuinely settled icons in the platform, and nothing about this screen gives it a
second reading. So the two glyphs are not the same problem and do not want the
same answer: one fails to say what it does and the other does not.

Labelling it would also undo what D-022 and D-037 both protected. A rare
one-way action must not carry the weight of the screen's payoff, and a word is
weight. Moving it back to an overflow is D-022, which D-037 replaced for a reason
that has not changed: a three-dot menu holding one item promises more than it
has.

**Why the FAB had to go rather than gain a label.** Not a preference. The
component measures its FAB slot with `minWidth` and `maxWidth` pinned to the same
square value, interpolated as the toolbar expands, and Material ships no extended
variant for it. A worded Start focus and that slot cannot both exist.

**The prominence survives the move.** D-037 rejected a `FilledIconButton` in the
content row because "the two actions read as a pair of equals with one tinted
differently". That is true of two icons. It is not true of a filled button
carrying a word standing beside a bare icon: the fill and the label together
separate them more plainly than the FAB's extra diameter did.

**What is unchanged.** The floating toolbar itself, and D-037's reasoning for it
over a docked one. The order, Delete leading and Start focus trailing, on D-037's
argument that in a horizontal bar the thumb lands nearest the reaching side.
Delete's `error` colour, its content description, the soft delete, and the single
undo offer. The `FabClearance` reserved beneath the last Plan row.

The glyph on Start focus loses its content description, because the word beside
it now names the action and announcing both would say the same thing twice.

**What would reverse this.** The toolbar growing crowded at large font scales or
in a narrow window, where a labelled button and an icon may not sit together. The
content row scrolls horizontally, so the failure would be a hidden action rather
than a broken layout, which is worth watching for rather than assuming.

---

## D-065. A list that has not read yet says nothing, rather than saying it is empty

**Decision.** The four task lists draw their chrome and no body until the stored
read has answered. Today, Inbox, Upcoming and Logbook each take the
`tasksLoaded` flag D-062 added, and the empty state is now behind it: the
branch order is failed read, then not read yet, then empty, then the list.

Nothing is drawn in the not-read-yet branch. No skeleton, no spinner, no
delayed spinner.

**What this supersedes.** The gap D-062 left open in as many words: "The four
lists still start on an empty loaded list and still flash a false empty state on
a slow read. They now have the flag that would fix it, `tasksLoaded`, and using
it is a different question." This is the answer to that question.

It also supersedes one paragraph of `docs/design/logbook.md`, which specified
"the M3 Expressive loading indicator, centred, and nothing else" for first load.
That was written before D-062 existed and was never built. Building it now would
give one of the four lists a spinner and the other three nothing, for a read all
four share.

**Why the empty state was the bug and the spinner is not the fix.** Every one of
these screens asserts something specific about the user's work when it is empty:
"Nothing scheduled for today", "Nothing completed yet". D-034 already named that
failure mode for a read that fails, and the initial state had exactly the same
shape as a read that failed, because `Loaded(emptyList())` and "we have not
looked" were the same value. D-062 split them for Task Details and left the
lists where they were.

So the defect is one sentence being untrue for as long as the read takes. The
answer is to not say it. A spinner would replace an untrue sentence with a true
one, and would also be a second thing appearing and vanishing inside the same
frame.

**Why nothing rather than a spinner after a threshold.** A delayed indicator is
the honest answer when a read can be slow, and this one cannot. It is a local
Room query over a personal task list, and D-062 measured the same read resolving
in a frame. A threshold long enough never to fire in the ordinary case is a
timer that exists for a case that does not happen, and one short enough to help
is the flash again.

The chrome is what makes the blank safe, and it is the part worth being explicit
about. The app bar, the navigation bar, the overflow and the add button are all
drawn before the read answers, so a read that never returns leaves a screen the
user can still leave, and a screen they can recognise as the one they asked for.
That is the same reasoning D-062 used for Task Details, and it is the reason
neither needs an indicator.

**Today keeps its banner rule, and this does not touch it.** D-040 puts the
reminder health banner above the empty state, because a user with no tasks yet is
exactly the user about to set a first reminder into silence. That branch is
untouched; it now runs one state later than it did.

**What would reverse this.** A list staying blank long enough for anyone to
notice. The read is shared, so the symptom would be all four at once, and the
fix would be the delayed indicator this entry declined rather than a spinner on
one screen.

---

## D-066. Backup says which half of the page is working

**Decision.** `BackupUiState` carries `working: BackupOperation?` in place of
`isWorking: Boolean`. Both buttons still disable while either operation runs, and
the one that is running now says so: Export becomes "Exporting…" and Restore
becomes "Restoring…", each beside a small indeterminate progress indicator.

**What this supersedes.** `docs/design/settings.md`, which recorded the gap and
accepted it: "Not built: any indication that an operation is in progress. Both
buttons disable while one runs and nothing else is drawn. Accepted for now on the
grounds that a local file of this size is written and read faster than a spinner
would be seen. If a large enough backup ever makes the page look inert, that is
where to look."

**Why that reasoning does not hold.** It measured the wrong file. The size of the
backup is not what decides how long this takes, because neither operation touches
a file the app owns. Both go through the Storage Access Framework, and the URI a
picker hands back can belong to any document provider on the phone, including one
backed by a network. A restore from a file on a cloud drive is a download, and
the page that says nothing during it is the page a user force-quits.

The second half is that "faster than a spinner would be seen" argues against the
spinner and not against the rest of it. Two buttons going grey with no word
between them is already a state change the user can see. It just does not say
what it means, and a user who taps a disabled button learns nothing from it.

**Why the label rather than only the spinner, and why both.** The label is the
part that answers the finding. A screen reader on a disabled button hears
"Restore from file, disabled", which is the app refusing without a reason; it now
hears "Restoring…", which is the reason. The indicator is what makes the same
answer available without reading, and an indeterminate one is honest, because
neither operation can report progress: the codec reads and writes in one pass and
knows the total only when it is finished.

**Why the running button stays disabled.** `BackupViewModel.run` already ignores
a second call while one is in flight, so the disabling is not what protects the
write. It is what stops the user asking a question the app has already answered.

**Why no live region on it.** A polite announcement here would fire twice, once
when the label changes to "Restoring…" and again when it changes back, and the
counted snackbar the screen already shows is the announcement of the result. The
second one would be the app saying the same thing in a worse form.

**What would reverse this.** A provider slow enough that an indeterminate
indicator stops reassuring anyone, which would mean the page needs a count rather
than a spin, and that would mean the codec streaming rather than parsing in one
pass.

---

## D-067. Start focus leaves the toolbar and becomes the screen's floating action button

**Decision.** Task Details draws two floating controls rather than one. The
`HorizontalFloatingToolbar` keeps Delete and nothing else. Start focus becomes an
`ExtendedFloatingActionButton`, a pill carrying the play glyph and the words
"Start focus", sitting beside the toolbar at its trailing end. Delete still
leads, Start focus still trails, and the pair sits bottom centre where the
toolbar alone used to.

**What this supersedes.** D-037 on the toolbar's contents, and D-064 on the
arrangement that replaced the attached FAB. Both entries are otherwise intact:
the floating toolbar over a docked one, the ordering and its reasoning, Delete's
`error` colour and its wordlessness, the soft delete and its single undo offer,
and the `FabClearance` reserved beneath the last Plan row.

**A docked toolbar was asked for twice and is still not the answer.** Worth
recording because the question will come back. Material's own usage split is that
a docked toolbar spans the full window width and is for global actions repeated
across pages, while a floating toolbar sits above the body content and is for
contextual actions belonging to that page. Delete and Start focus act on the one
task the screen is about, so D-037's reasoning was right and is now sourced
rather than paraphrased.

Two corrections to carry with it. The baseline bottom app bar is no longer
recommended, so the choice is docked toolbar or floating toolbar, not bottom app
bar or floating toolbar. And Material does *not* object to a bottom-aligned
toolbar on this screen: it says to show the navigation bar on primary pages and
toolbars on secondary pages with actions, which is exactly Today, Inbox and
Upcoming against Task Details. An argument was made in session that a docked bar
would collide with the navigation band's position in muscle memory. That is a
product judgement, it may still be worth something, and it is not Material's
position. Do not cite it as one.

**Why, and it is not the reason it looks like.** The obvious reason is that a
toolbar with room in it can hold actions this screen may want later. That is not
the argument, and building for actions that do not exist is what the drift guard
in `CLAUDE.md` exists to refuse. The arrangement has to be right with one item in
the toolbar today.

It is right because it makes the hierarchy structural rather than stylistic.
D-037 and D-064 both had to argue the ranking of these two controls from how they
were drawn: D-037 gave Start focus the FAB slot's extra diameter, and when that
slot turned out not to hold a label, D-064 argued that a fill and a word beside a
bare icon separate them well enough. Both are true and both are tuning. Once
Start focus is the screen's floating action button and Delete is an item in a
toolbar, the two are different kinds of control, and nothing about their styling
has to be defended to keep the destructive one subordinate. D-022's rule that a
rare one-way action must not carry the weight of the screen's payoff stops being
maintained and starts being a consequence of the layout.

**It also retires D-064's reversal condition.** That entry ended by naming what
would undo it: "The toolbar growing crowded at large font scales or in a narrow
window, where a labelled button and an icon may not sit together. The content row
scrolls horizontally, so the failure would be a hidden action rather than a
broken layout." A toolbar holding one icon has nothing to crowd, and the pill
sizes to its own label outside it. The failure mode is gone rather than watched.

**The cost, which is a real one: the pill is dimmer than the button it replaces.**
Start focus was a filled `Button`, so `primary`. A floating action button's
container is `primaryContainer`, and `expressive-components.md` records that
overriding that to `primary` was tried on `AddTaskFab` and reverted, with the
consequence stated outright: the button "is therefore never the highest-contrast
element on the page. That is what Material intends." So the screen's payoff steps
down one level of contrast. Accepted, because what D-064 was actually buying was
the word, and the word survives unchanged. Taking the contrast back would mean
departing from the specification on a second component after deciding not to on
the first.

**Why an extended button here when Add is a regular one.** `AddTaskFab` was moved
off the extended form because the words "Add task" spent 80dp repeating what a
plus on a task screen already said. That reasoning does not transfer, and the
difference is the whole of D-064: a play triangle on a screen about one task does
*not* say Focus. It reads as preview, as resume, as run. The extended form is
justified on this screen exactly where it was not on Today, and the two are
consistent rather than in conflict.

**What Material actually documents, checked rather than remembered.** The
toolbars guidance at `m3.material.io/components/toolbars` names this exact
pairing: a FAB placed next to a floating toolbar, carrying one high-priority
action alongside the toolbar's set, and it says to use the FAB for the
highest-priority action in the view. So this is the documented configuration
rather than a departure from one, which is the opposite of what D-064 had to
settle for when the attached slot could not hold a label.

The same page supplies an argument this entry would otherwise have missed, and
it is the strongest one here. Material warns against emphasising two controls at
once with bold primary colours, and names a button and a FAB together as the case
to avoid. The build before this change is a filled `primary` button sitting inside
the toolbar. Adding a FAB beside it would have been precisely that mistake;
moving Start focus out and leaving Delete as a bare icon is what prevents it.
The change resolves a conflict rather than introducing one, and the contrast the
entry gives up above is part of how it does that.

It also confirms the growth path, without licensing the toolbar to be built for
it: when a floating toolbar runs out of room, the trailing actions collapse into
an overflow menu. And the 16dp the group sits above the bottom of the content is
the specified minimum margin for a horizontal floating toolbar, so
`FocuslistSpacing.md` there is the rule rather than a choice.

**Why two floating elements are acceptable when two bare buttons were not.** The
arrangement considered and rejected before this was a circular Delete and a
Start focus pill with no container between them. Two buttons with their own fills
and no other distinction read as peers, which is the pairing D-037 refused when
it rejected a `FilledIconButton`. A toolbar container standing beside a floating
action button is not that. It is Material's own pairing, the two have different
roles, and Delete's container is a neutral toolbar surface rather than a fill of
its own. The only departure from the documented pattern is a pill where it shows
a circle, which is the same departure D-064 already needed and could not get from
the attached slot.

**One implementation trap, found on the device and worth writing down.**
`ExtendedFloatingActionButton` has a `text`/`icon` overload that looks like the
obvious way to build this. It wraps the label in `Modifier.clearAndSetSemantics {}`,
so the word is drawn and then removed from the accessibility tree; Material's
intent is that the icon's own `contentDescription` carries the announcement and
the visible label is decoration.

Built that way first, with the glyph's description null because D-064 says the
word names the action, the result was a button that said "Start focus" on screen
and announced nothing whatsoever to a screen reader. That is D-064 inverted: the
entry exists because the control did not say what it did, and this would have
made it say nothing at all to the people who most need it to.

Four `TaskDetailsSemanticsTest` cases caught it, all reporting the word present
in the unmerged tree and absent from the merged one. The fix is the content-slot
overload, which composes the same row and clears nothing. Anyone tidying this
toward the shorter overload will reintroduce the defect silently, because it
looks identical on screen.

Worth noting how close this came to shipping unseen: the emulator's
instrumentation was crashing before collecting a single test, and the failure was
only visible once the suite was run on a physical device.

**What would reverse this.** Delete being tapped by people reaching for the pill,
which would mean the gap between the two controls is too small to separate them
and the answer is spacing, not a return to one container. Or the toolbar still
holding one item after several phases, which would mean the container is
scaffolding for actions that never arrived and Delete would be better as a plain
floating control.

---

## D-068. Task Details says Resume focus when that is what the tap does

**Decision.** The Task Details action reads "Resume focus" when this screen's
task already has a focus session, and "Start focus" otherwise. The condition is
the one `beginFocus` itself branches on, `focusedTaskId == task.id && session
!= null`, so the word cannot disagree with the behaviour.

**What this supersedes.** Nothing. D-064 gave the control a word and D-067 moved
it onto the FAB; both write the label as the literal text "Start focus" because
neither entry asked what the control does when a session is already open on this
task. This answers that and leaves the rest of both entries standing.

**The defect.** D-057 gave `beginFocus` three branches: no session starts one, a
session on another task raises the switch question, and a session on *this* task
resumes rather than restarting. The label was not part of that change, so a task
with a paused session offered "Start focus" and delivered a resume. Tapping it on
a forty-five minute session paused at 44:45 does not produce 45:00, which is what
the words promise.

This is the family of defect the D-056 to D-059 audit was about: the app claiming
something it has not done. It is the mildest of them, because nothing is lost and
the behaviour underneath is right, and it is still the same mistake.

**It is also D-064 applied where D-064 did not look.** That entry exists because
a play triangle did not say what it did, and it fixed that by adding the word. A
word that names the wrong act is not an improvement on a glyph that names none.

**Why the same-task running case reads Resume too.** In practice it is barely
reachable, since D-015 pauses the session when the sheet closes, so a session on
this task is a paused session almost always. Rather than adding a third state for
a case that hardly occurs, the label branches on exactly what the code branches
on. A label whose condition is a copy of the behaviour's condition cannot drift
from it; one that adds a distinction of its own can.

**Why the switch case keeps Start focus.** A session on a *different* task still
reads "Start focus", correctly. That tap does not resume anything, and D-057's
dialog is what explains the choice. Wording it Resume would name a session the
user is not looking at.

**Why a new string rather than borrowing one.** "Resume focus" already exists
twice, as `today_focus_now_resume` on the paused card and `focus_resume` in the
Focus sheet, each under the screen that owns it. `task_resume_focus` follows that
convention rather than reaching across a screen boundary for wording. The three
must stay identical, and that is a translation note, not a reason to share a key.

**What would reverse this.** The label proving to be the wrong place to carry
this, because what the user needs to know is how much of the session is left
rather than which verb applies. Today's card shows the remaining minutes; this
shows none. If that turns out to be the real gap, the answer is a line on the
screen, not a longer button.
