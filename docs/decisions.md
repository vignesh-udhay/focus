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
    Focuslist cannot tell whether it is on.

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
