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
