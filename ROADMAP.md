# Roadmap

Five phases to 1.0, sized for one person at 10 to 15 hours a week.

The reasoning behind this ordering is in `docs/decisions.md`. The product
scope it delivers is in `PRODUCT.md`.

---

## Current phase

**The accessibility sweep is done. The TalkBack pass is not, and the difference
matters.** Method, because it decides what the findings are worth: every screen was opened on the emulator and its
accessibility node tree dumped, which is the tree an accessibility service reads, and
each focus stop's announcement composed from its own label and its descendants'. That
catches silent controls, targets under 48dp, and two controls that say the same
thing. It does not catch reading order, heading structure, or live regions, and it is
not a substitute for someone turning TalkBack on and using the app.

Swept: Today, Inbox, Upcoming, Logbook, Settings, Reminder health, Task details,
Focus in two states, Quick Add, and the Duration picker.

**Reminder health was reading its status glyph out loud.** Each check row carries a
mark beside its words, and D-021 is explicit that the mark is a second channel for
someone scanning, on top of words that already differ. The tick was hidden from the
start because an `Icon` takes a `contentDescription` and it was given null. The
question mark and the exclamation are `Text`, so they went into the tree as a bare
"?" and a bare "!" and were announced after the row they annotate. Both now carry
`clearAndSetSemantics`, and `ReminderHealthSemanticsTest` is new: three cases, run
against the unfixed code first, where they fail.

**One finding recorded rather than fixed.** The Duration picker's buttons announce
"45m" where the task rows announce "45 minutes", and `DurationLabel`'s own KDoc says
the spoken form has to be attached as a content description. It cannot be, through
this API: `ButtonGroupScope.toggleableItem` takes no modifier, and the only opening
is `customItem`, which would mean building the connected toggles by hand. That is
what adopting `ButtonGroup` was for. The note is in `TaskDetailsSheets.kt` beside the
code.

**One false alarm, checked rather than filed.** A silent 996x13px focus stop on Today
turned out to be the Completed disclosure clipped at the scroll boundary; scrolled
fully into view it announces "Completed, 1" and the screen is clean.

**The Focus readout fix was confirmed live** in the same sweep: the status line
carries `text='44:40 · 45 min focus'` and `desc='44:40 remaining, 45 min focus'`. The
mascot does not appear in the tree at all, which is what a decorative drawing should
do.

**What is left of the pass, so the next session does not mistake this for finished.**
Everything the node tree cannot answer: traversal order, which the dump only hints at
and which puts the app bar last on several screens; whether headings are marked such
that a reader can jump by them; live regions, for the undo snackbar and the health
banner, neither of which announces itself today as far as this method can tell; and
the gestures, swipe-to-next, explore-by-touch, and the local context menu. All of it
wants TalkBack actually switched on, on a device, by a person. Phase 5's exit
criterion is "every screen is navigable and comprehensible with TalkBack", and a
sweep of the tree is evidence toward that, not the thing itself.

**Phase 5 has begun, and its first exit criterion is met: a backup restores onto
a wiped install.** Never verified end to end before. The instrumented tests all
run in process, so none of them exercised export, wipe and import as separate
acts, which is what the criterion actually asks about.

Done on the emulator against the seeded fixtures plus one uniquely named task, with
the theme set to Dark so the preferences half of the file had something to carry.
Exported through the real picker, `pm clear`, relaunched to confirm the wipe had
taken and the debug seed had refilled the database with different data, restored
through the real picker, then exported a second time and compared the two files.
Same format and version, identical settings, the same fourteen task ids, and **zero
rows differing on any field**. The theme came back as Dark against a light system,
which is the preferences half arriving.

**A pre-rename backup still restores, which is the claim the rename rested on.**
`Focuslist-backup-2026-09-09.json` was written by the app before the rename to Catimo and
restores cleanly now, swapping the list to its own older snapshot. The export
filename moved with the app name and the `focuslist-backup` marker inside the file
did not, which is why. Had that marker been renamed with everything else, every
backup a user already held would have stopped opening, and this is the test that
would have caught it.

**What a backup does not carry, noticed while doing this.** A paused focus session
lives in `SharedPreferences` through `FocusSessionStore`, not in the file, so it does
not survive a restore. That is defensible, a session is not data the user typed, but
it is undocumented and worth a line somewhere before 1.0.

**The paused Focus card stopped returning behind Today's app bar.** A defect,
no decision entry: leaving Focus has always paused the session and made its card
the route back.

Resuming removes the paused card from Today. Leaving Focus inserts it again
ahead of the first band, and `LazyColumn` normally preserves the old first
item's key. With enough tasks to scroll, that kept Overdue in the same place and
laid the returning card out above the viewport, behind the app bar. The existing
viewport helper now treats a paused card appearing while Today is at the top as
the same special case it already handled for a task moving to Completed: it pins
index zero for the next remeasure. A list deliberately scrolled deeper keeps its
position.

The reveal animation stays. It communicates the real state change—leaving Focus
pauses the session and restores its way back—but it now expands into the visible
viewport. Confirmed first with a scrollable regression that failed with the
Resume action at y=169 behind an app bar ending at y=263, then with 31/31 Today
instrumented tests, 665 JVM tests, a debug build, and two Focus exits on the
emulator.

**Three fixes found by asking what was broken rather than what was next.** No
decision entries: one restores behaviour `strings.xml` already claimed, one
regenerates an asset, one deletes wiring a decision orphaned.

**The Focus readout had no spoken form, and the strings for one had been sitting
unused since they were written.** `strings.xml` carried `focus_readout_remaining`
and `focus_readout_elapsed` under a comment saying the readout "is digits, so it
carries a spoken form for TalkBack", and `git log -S` finds no Kotlin that ever
referenced either. So TalkBack was given a bare "44:37", which does not say whether
it is time left or time spent, and those are opposite readings of the same four
digits. The status line now carries a description, and which word it uses follows
`focusReadout`'s own branch: the two open-ended states count up and say elapsed,
everything else counts down and says remaining. One string was added,
`focus_status_line_spoken`, joining with a comma because the middle dot the line is
drawn with does not read as a pause aloud.

This is the failure mode D-040 named, a behaviour recorded in a document and never
built, and it is worth noticing that nothing caught it: every other assertion in
`FocusSessionSemanticsTest` passes with the readout unannounced. Three tests now
pin it, including one that a missing description would fail on its own.

**D-023's row menu left callbacks behind, and one of them was load-bearing in a
document.** `TodayScreen` threaded `onFocusTask`, `onDelete` and `onReschedule`
from the view model into `TodayContent`, and nothing in the body ever called them;
Inbox and Upcoming carried the same dead `onReschedule` and `onDelete`.
`TaskListRow` takes a toggle and an open and has for some time. All of it is gone,
with the five strings that menu used and two more orphans found in the same sweep.

The part that mattered: with `onFocusTask` dead there is **no way to start Focus
from a Today row**, and `focus.md` listed one. That line was added during D-048 on
the assumption the callback was live, and it has been corrected. Focus has two
entries, Start focus on Task Details and the paused session card.

**Ready is gone, and Focus has five states.** D-054, and this one is a decision
rather than a cleanup, because removing the state removes a capability: a session
can no longer be opened stopped.

It had no entry point. D-048 removed the card branch that called `openFocus`, and
nothing else ever did, so the state had been drawn, tested and unreachable since.
`focus.md`'s own rule settled it, that "a drawn state nothing can arrive at is a
state that should not exist". The alternative, giving Ready a route back, was
refused on the grounds D-023 already wrote down: picking a task and choosing Focus
on it is the deciding already done, and a state whose job is confirming a decision
the user has made is friction.

`FocusState.Ready`, `openFocus`, `startFocusSession`, the Start focus control and
its string are all gone. `focusStateOf` and `focusReadout` stop taking a nullable
session, which is the part worth keeping an eye on: it encodes the new invariant
that opening the sheet implies a session. That holds because `_isFocusSheetOpen`
is not persisted, so a restart cannot restore the sheet onto a session that is no
longer there, and all remaining paths start or resume a clock before opening.

Verified: 665 unit tests green, `FocusSessionSemanticsTest` 10/10 on the emulator,
and Focus opened on a device to confirm it lands Running with Complete and Pause
rather than on an empty sheet. Two instrumented tests changed rather than moved:
the whole-status-line assertion was Ready's, because Ready was the only state whose
clock did not move, and it now asserts Paused's line as a pattern since those
digits are whatever the clock read when it stopped.

**"tomorrow at 10 am" captures the hour and keeps the day.** A defect, no
decision entry: this restores behaviour the code already claimed.

The capture parser peels the time off the end of the title first, then reads the
day from what is left. The peel reached back two words, on a comment asserting
that "at 3pm" was the longest time form. It is not. A meridiem written apart
from its hour is a word of its own, so "at 10 am" is three words. The peel
matched the trailing "10 am" and left the preposition at the end of the head,
where it shadowed the day standing directly in front of it, because `parseDate`
matches whole candidates and "tomorrow at" is not one.

The same fault with no day involved was visible the whole time and went
unnoticed: "Call the dentist at 10 am" captured a title ending in "at" and
marked only "10 am", against the rule three lines above the loop that the mark
covers the preposition the user typed.

**Why the suite missed it.** `parseTimeOfDay("3 pm")` is tested directly and
passes. Every test that went through `splitTrailingCapture` used the closed-up
"3pm", so the spaced meridiem was verified at the unit that handles it correctly
and never on the path that broke it. Two tests now cross that path, both
confirmed failing against the unfixed parser first.

**Quick Add stopped naming the wrong destination, and its Done key commits.**
D-053 for the first, a plain defect fix for the second.

The sheet is opened from two screens that resolve a missing day differently.
`TodayScreen` saves `parsed.date ?: today`, so an undated capture lands in
Today. `InboxScreen` saves `parsed.date`, which is null, so it stays in Inbox,
which is the decision Inbox exists to defer. The supporting line read neither
and went by `parsed.date` alone, so it said "Saved to Today" over a capture
going to Inbox. It now takes a `fallbackDate` from its host, which is the same
value the host acts on rather than a second copy free to drift, and it says
nothing at all until there is a title to describe.

**Ranked above the Done key deliberately.** `CLAUDE.md` puts a missed reminder
above a crash because a crash is visible. A capture surface naming a destination
the task is not going to is invisible wrongness; a Done key that does not commit
is visible friction, felt and worked around every time.

The Done key: the field declared `ImeAction.Done` and had no `keyboardActions`,
so the key that looks like it commits only closed the keyboard, and capture
ended by reaching past it for the button. It is guarded by the button's own rule,
so the two commit paths agree about what is saveable.

**A miss worth recording.** D-052 moved "New task" from the field label to a
heading, and `QuickAddSemanticsTest` had been using that label as its handle on
the field in ten places. Running the unit suite and one instrumented class did
not catch it. The file now finds the field by its edit action, which does not
care what names it.

**Quick Add names itself with a heading again, and the field stops carrying
it.** D-052. Reported as the "New task" label making the input noisy. The field
was showing five things at once: a placeholder teaching the date trick, a
supporting line naming the resolved day, the parsed run marked in `primary`, a
reminder chip beneath it, and a floating label repeating what the sheet was for.
The label was the only one saying nothing about the task being typed.

It is a `titleLarge` heading above the field now, with `heading()` semantics like
RepeatSheet and the Task Details sheets. **The field is the same size and
emptier**: a Material 3 filled field is 56dp with or without a label, so the
container did not move and the top row went back to the text.

This reverses a line in `expressive-components.md` that said the sheet had lost
its heading because the FAB already says "Add task". Right about the FAB, wrong
about where the name went: it moved into the label, where it was redrawn on
every keystroke and cost a row of the input instead of a line of the sheet. Both
the entry and that section now say so.

**Quick Add from the widget opened once and then kept reopening.** Reported as:
tap add on the widget, close the drawer without typing, go to Inbox, come back,
and the drawer is open again. The request travels from the widget as a counter
the nav host keeps in `rememberSaveable`, and Today read it with
`LaunchedEffect(quickAddRequest) { if (quickAddRequest > 0) ... }` and never
retired it. Today is a navigation destination, so it is disposed on leaving and
built again on returning, and every one of those compositions replayed a request
the user had made once and already answered. `rememberSaveable` meant it survived
process death as well, so the drawer would have kept coming back the next day.

Today now calls `onQuickAddRequestHandled` when it opens the sheet and the host
sets the counter back to zero. Still a counter rather than a flag, so pressing
add again while Today is open changes the key and fires the effect a second
time; `QuickAddRequestTest` covers both halves, and the replay half was run
against the unfixed screen first, where it fails on `assertDoesNotExist`.

No decision entry: nothing about what the product does changed. The interaction
table already said the add button opens Quick Add, and it now does that once.

**The widget was stuck on "loading" on the phone, and could have stayed that way
forever.** D-051. `dumpsys appwidget` showed the instance with no `views=` line
at all, next to other widgets that had one: nothing had ever been published to
it, so the launcher was still drawing `initialLayout`.

WorkManager's diagnostics said why nothing ever would. The `SessionWorker` for
that widget had already run and returned SUCCEEDED, because a Glance session that
times out is a successful worker. And the timer that expired is the five second
idle one: `runSession` starts the 45-second clock only after the first
`processEmittableTree`, so the whole period before the first draw is unprotected.
D-045 put a blocking read in exactly that period.

**Nothing retried it.** `updatePeriodMillis` is zero and the application observer
fires only on a task or Focus write, dropping its first emission. One lost
session left the widget on the spinner until the user happened to change a task.

`provideContent` is now called immediately and the null snapshot is a drawn
state, `WidgetBody.Loading`, which is the header alone. **Measured by making the
read slow on purpose**: with twenty seconds in front of the flow, the first
publish lands at t+7.8s on the new shape and t+27.7s on the old one, timed by
polling for the widget gaining a `views=` line. The 7.8s is cold start; the old
shape spent the whole twenty on top of it having published nothing.

**The widget checkbox now carries both colours, so it follows the system.**
D-050. Reported as invisible in light mode, and it was invisible in whichever
mode the widget was not built in. `ColorProvider.getColor(context)` collapses a
day/night pair using the app process at composition time, while the RemoteViews
are drawn later by the launcher in whatever mode the system is in then.
Everything else in the widget is emitted as a pair and deferred; the checkbox was
resolved to one `Color`, which has no night variant, so Glance wrote the same
value into both slots and it froze.

On a phone with scheduled dark mode that breaks every sunrise: the container
flips, the text flips, the outline keeps last night's colour. The fix passes a
`DayNightColorProvider`, which the same Glance check explicitly allows. Only the
resource-backed form was ever rejected, and resolving to a single colour threw
away more than the check was asking for.

**Seen, not reasoned about.** Built in dark and switched to light with nothing
touched: fixed, the outlines darken with the surface; unfixed, they stay pale and
vanish. Then the mirror, by putting the old two lines back and running the same
toggle: dark navy outlines on a dark navy container.

**The widget is a compact Today now, with bands and no Focus at all.** D-049.
Asked for directly, and it lands where D-048 was already pointing: that entry
deleted `FocusNow`, `FocusNowReason` and `focusNow`, which took away both of the
reasons D-031 had given the widget a lead card for. `ReminderPassed` was cut
outright and `ResumePaused` is kept for a screen the user opened, so carrying it
onto a home screen would have been a new argument rather than a surviving one.

Overdue, No time set and Later today, labelled with Today's own strings, over
rows in the order `todaySections` already produced. Completed stays off: on Today
it is a disclosure the user can open and a widget has nothing cheap to open into.
The lead card, the reason line, Resume and `WidgetLaunchCommand.ResumeFocus` are
all gone, and the widget reads no Focus session anywhere.

**Two things got simpler rather than more complicated.** The completion evidence
stopped carrying an index: the just-completed task is drawn as though it were
still outstanding, which returns it to its own band in its own place, so
`widgetCompletionIndex` and `WidgetCompletion.previousIndex` are deleted rather
than adapted. And `widgetSnapshots` lost an input, because nothing in the widget
depends on Focus any more.

**A band heading needed its own tap target**, found by probing rather than by
reading. `AbsListView` eats every touch inside the collection before the root's
"anywhere else opens Today" can see it, so a heading is inert without an action
of its own. That is the second thing lost to the same rule after D-047, which is
why it is now written down as a rule: anything drawn inside that list needs its
own action or it is dead.

**Verified on a launcher.** The bands render, scroll, complete, uncheck, keep the
checked row in its own band with its scroll position held, and the empty space and
the headings both open Today. The main source set compiles clean.

**And now verified in dark, and at a small size**, which is Phase 4's last exit
criterion and was still standing on a check of the pre-D-049 widget. Placed on the
emulator at 4x3 and again at roughly 3x2, in both ui modes. The container flips
with the system, the band headings stay legible bold-on-tinted in both, and a
narrow widget ellipsizes row titles rather than wrapping or clipping them. Phase 4
can be called done.

**The widget picker's preview image was stale again, and has been regenerated.** It
showed an unlabelled run of five tasks, which is the widget D-049 replaced: no band
headings anywhere in it. Recaptured from a 4x3 widget on the emulator against the
debug seed, cropped to the host view's own bounds and masked back to the rounded
corners the asset had, at the same 364x303. The bands are in it now. It still ends
on a half-drawn row, because the real widget does: the list scrolls, and a preview
that stopped on a clean row boundary would be advertising a size the widget does
not have.

This paragraph used to end by saying the JVM suite was blocked on D-048's
leftovers: `FocusNowTest.kt` and about a dozen cases in `TaskListViewModelTest.kt`
still calling the deleted `focusNow`, and `pausedFocusTask` with no test of its
own. All three were finished in the D-048 entry below, and the whole unit suite is
green again.

**Today's Focus now card is the paused session card, and it stopped taking its
task out of the list.** D-048. Asked for as a redesign, and most of the work was
deletion: the card said "what should I do now" for two reasons, and the second of
them named a task sitting in the Overdue band directly below it, under a label that
already said the work was late.

`ReminderPassed` is gone, and with one reason left there was no reason to state, so
`FocusNow` and `FocusNowReason` went too. `focusNow` is `pausedFocusTask`, which
takes tasks and a paused id and returns a task. The card is a label, a title and
Resume: no checkbox, no reason line, no tap on the body, all three of which only
existed because D-012 lifted the task out of its band and left the row's
affordances with nowhere to live. The remaining minutes moved up into the label,
because that is the number the resume decision turns on and the label line was
already there holding "Focus now".

**Two side effects worth knowing about.** `todaySections` lost `promotedTaskId`, so
every task Today holds is now in exactly one band with no exception to carry. And
the `focusNow` flow lost its clock: it read `LocalDateTime.now()` and combined the
day purely as a trigger, with a documented cost that a reminder passing did not
re-run the rule on its own. `ReminderPassed` was the only reason that read a clock,
so the card can no longer be late.

**Ready is now unreachable, and that is an open question rather than a bug fixed
here.** `openFocus` is the only route to it and nothing in the app calls it any
more: both remaining entry points call `beginFocus`, which starts the clock. The
card's non-paused branch was the last caller. `focus.md` says a drawn state nothing
can arrive at should not exist, so either something lands on Ready again or D-013's
six states become five. Recorded in D-048 and in `focus.md`; not decided.

Separately, `focus.md` claimed Start focus on Task Details lands on Ready and that
was already untrue before any of this. The document is corrected, the code was not
touched.

**Verified: 667 unit tests pass, and 30 Today instrumented tests pass on the
emulator**, including the pair that used to assert the card opened its task and now
assert the title appears exactly once, in the row.

**Looked at on a device, in both ui modes.** A session started from Task Details and
left draws "Paused · 45 min remaining", the title, and Resume focus, with the same
task sitting in its band below. The open question was whether a card with no
checkbox would compete with its own row; it does not. The card is tinted, has a
button and no checkbox, and reads as the session rather than as the task. What is
true and worth watching is that the title is now on the screen twice, which is
exactly what D-012's promotion existed to prevent and what D-048 accepted.

**The empty space below a short list stopped opening the app, and now does
again.** D-047. Reported as "pressing the bottom empty space on the widget does
not open the app", and the interaction table in `widget.md` has always said it
should.

D-043's weighted `LazyColumn` is a `ListView` stretched over every pixel below
the header whether or not it has rows for them, and `AbsListView.onTouchEvent`
returns true whenever the view is enabled, regardless of whether anything in it
is clickable. It ate the touches, and there was no row under the finger to carry
a fill-in intent either, so nothing fired. With three tasks on the default widget
the list was handed 638px and used 420, leaving about a quarter of the widget
inert. The list measures to its content now and the remainder belongs to the root
again. Scrolling a long list is unchanged.

**An API 29 emulator was installed rather than guessing.** Glance branches on
`SDK_INT > S`: 32 and up carry the rows inside the RemoteViews, while 29, 30 and
31 fetch them from a bound service that fills the adapter asynchronously, where a
content-height list could in principle measure zero and render a bare header. It
does not. On Android 10 a long list still caps and scrolls, a short one measures
to its rows, a freshly dropped widget draws them within about a second, and the
dead region opens Today.

**That emulator also closed a gap the docs had been carrying, and opened a
smaller one.** API 29 had never been looked at. It renders, but Glance's
`cornerRadius` is a no-op below API 31, so the widget is a hard-edged rectangle
there and the add button is a square. Cosmetic, unfixed, and now written down.

**Focus draws the cat now, and the shape is gone. D-046.** The Cookie said
whether the clock was running and nothing else, which is a job the app's own
mascot does better and warmer. Two poses, `cat-sit-front` at rest and `cat-nap`
while running, exported from the same board as the five empty-state poses and on
the same 0.3643 scale factor. The remaining time left the shape and joined the
status line at body size, so the line now reads `44:37 · 45 min focus` and the
task title is finally the largest thing on a screen built to stop clock-watching.

**It was almost free because the mascot system already existed.**
`EmptyStateMascot.kt` resolves a pose against `primaryFixed`, `primaryFixedDim`
and `onPrimaryFixedVariant` at runtime, which is what makes the cat follow
dynamic colour where baked artwork could not. One function came out of it,
`rememberMascotImage`, because `FocusMascot` lays its two poses out itself and
cannot use `MascotImage`'s sizing. Everything else is two path sets, a crossfade
and a moved `Text`. `FocuslistMotion.mascotSettle` is the one new token;
`FocuslistDimensions.FocusShapeSize` is gone.

**Sequenced deliberately against D-013's tripwire**, which said that if Phase 4
slipped while Focus grew, D-004 was right about sequencing after all. The reason
it landed here anyway: Phase 5 ships the Play listing, the Focus screen is in
those screenshots, and shipping the Cookie means reshooting them.

Verified: the debug build compiles, the Focus unit tests pass, and
`FocusSessionSemanticsTest` now matches the budget as a substring because every
state's line begins with a readout. **Not verified by eye:** nobody has watched
the crossfade on a device, and nobody has checked the thing that actually
decides this — whether a napping cat reads as *running* at a glance. If it does
not, the fix is to swap the poses and accept that it is duller.

**The widget was frozen for 45 seconds after every refresh, and had been since
it shipped.** D-045. Reported as three separate complaints, which turned out to
be one bug: tasks added in the app did not appear, unchecking a row the user had
just checked did nothing, and a widget showing one task of four caught up the
moment it was tapped.

Everything above `provideContent` runs once per *Glance session*, not once per
update. `runGlance` calls `provideGlance` a single time, `provideContent` then
suspends and never returns, and a session lives 45 seconds past its first
composition. `updateAll` does not re-enter any of it: it posts an event that
forces a recomposition, and recomposing re-rendered the value captured up there
rather than reading again. An update arriving during a session changed nothing;
one arriving after a session expired started a fresh one and looked perfect,
which is why it read as intermittent. The data is now a `Flow` collected inside
the composition.

**Measured on a launcher, both before and after.** Two completions in the app
eight seconds apart produced byte-identical home screen captures; waiting sixty
seconds for the session to expire and writing once more brought every missed
change through at once. After the fix the same second write lands, and a check
followed four seconds later by an uncheck now returns the row to normal instead
of sticking for minutes.

**Older than D-043.** `git log -L` puts the shape at `e4f493c`, the widget's
first commit. Scrolling did not cause it, it exposed it: a stale list is visibly
stale where a stale list truncated to `+N more` just looked like a small widget.

**Two smaller things went with it.** `LeadItemId` was `Long.MIN_VALUE`, which is
exactly `LazyListScope.UnspecifiedItemId`, so the constant named for a stable id
was asking Glance to invent one; it is `Long.MAX_VALUE` now. And the widget
picker's preview image still advertised `+N more`, a feature D-043 deleted, so it
has been regenerated from the current widget.

**Verified, including that the new test can fail.** `WidgetSnapshotsTest` asserts
six times that a change to a source reaches a collector already listening. Four
of the six fail when the flow is reduced to a single read, which was checked
before they were trusted. Full unit suite green.

**The widget's task list scrolls, and stopped measuring itself.** D-043.
Reported as "why can't I scroll through the tasks", and the answer was that it
was never built to: a `Column` drew what fit and a `+N more` line named the rest,
which told the user work existed and gave them no way to reach it. The rows are a
Glance `LazyColumn` now, a `ListView` in RemoteViews terms, so every outstanding
task is handed over and the overflow scrolls.

**This deletes the arithmetic that three entries had been arguing about**, and
that is the finding rather than a side effect. D-036 replaced a breakpoint table
with division over the reported height. D-041 found the division had been fed one
of two constants for its whole life, because `SizeMode.Responsive` also decides
what `LocalSize` reports. Both were corrections to a calculation that only had to
exist because the widget was deciding how many rows to draw. A `ListView` works
that out without being told the height. `rowCapacity`, the `heightDp` and
`fontScale` inputs, the private copy of every composable's height, and the
obligation to keep that copy in step are all gone, and `sizeMode` is
`SizeMode.Single` because nothing reads the size at all.

**That supersedes D-041's `SizeMode.Exact` one entry after it shipped**, which is
worth stating plainly rather than burying. D-041 was not wrong; `Exact` was the
right answer while `rowCapacity` existed, and finding that it had never received
a real height is what exposed the measuring itself as the liability.

**D-031's count clause goes, its argument does not.** That entry allows the
widget one count, "disclosing rows that did not fit", and the clause goes with
the thing it described. The reasoning underneath, that a surface nobody asked to
look at needs a higher bar to speak up, governs what the widget *asserts*: the
lead still speaks only for a paused session or a passed reminder, the end states
are still two quiet lines, and the resting view is unchanged. Scrolling is the
user deciding to look further, which is the deliberate act D-031 contrasts a
widget against.

**Verified, including that the new test can fail.** 665 unit tests. `WidgetScrollTest`
composes the widget to `RemoteViews`, inflates them and asserts an `AdapterView`
is in the tree; it was run against a `Column` first and fails there with
"FrameLayout / LinearLayout", so it is measuring the collection rather than
passing vacuously. That check was worth doing: earlier in this session a
notification test passed against a shade that had never received anything.

**Since verified on a launcher**, while chasing D-045. The Pixel launcher's view
hierarchy reports an `android.widget.ListView` inside the hosted widget, seven
tasks scroll, and a row's checkbox does complete and reopen from inside a lazy
item, which was the part worth doubting. The one thing still unverified is a
resize drag: the handles appear on all four edges, but synthesised input could
not complete the drag itself.

**Reminder Health now says when a missed-reminder notice clears.** The seven-day
`ConcernWindow` was defensible and invisible: the Today banner could not be
dismissed, a successful test did not remove it, and nowhere did the app tell the
user that it expires. The missed-state body now says it clears automatically
seven days after the incident, with the number read from `ConcernWindow` so copy
and behaviour cannot drift. The Today banner stays concise and opens the screen
that explains the rule.

**That missed incident can now be acknowledged from Today, under D-044.** Its
close button persists the current delivery ID, so the matching banner stays out
of the task list across restarts while the unmodified incident remains in
Reminder Health for the rest of the seven-day window. A newer miss has a new ID
and appears again. `ActionNeeded` still has no dismiss action because it names an
active delivery failure rather than a past incident; if one sits underneath the
miss in the health priority order, acknowledging the miss reveals it.

Verified: 668 JVM tests with zero failures, including the incident-ID and
overlapping-failure rules; a successful debug build and Android-test compile;
and all 30 `TodayScreenSemanticsTest` cases on the Pixel 10 emulator, covering
independent open and dismiss targets at 100% and 200% font scale. On the OnePlus
8T, the close target was exposed as “Dismiss missed reminder notice”, removed
the banner immediately, retained the incident and seven-day explanation in
Reminder Health, persisted its delivery ID to disk, and remained dismissed
after the final replacement install restarted the process without clearing data.

**OnePlus reminders now use the alarm-clock delivery path, under D-042.** The
reported phone was the same OnePlus 8T D-009 measured, and the failure had moved
from a probe into ordinary use: reminders chosen on minute boundaries posted
about five seconds, two minutes and two and a half minutes late. Permissions,
standby, notification access and the Doze allowlist were all healthy. OxygenOS
was again turning `setExactAndAllowWhileIdle` into an inexact alarm.

On OnePlus only, exact reminders now use `setAlarmClock`; every other vendor
keeps the ordinary exact path. A five-minute probe on the phone scheduled with
`window=0`, equal earliest and latest trigger times, and the alarm-clock record
present. A 30-second end-to-end test then posted its notification 1,478ms after
the target, including receiver and notification-service work, against the
multi-minute drift reported on the old path. The cost is Android showing the
earliest task reminder as the device's next alarm, so the branch does not extend
by guess to OPPO or realme. The test reminder shares the production path, and
JVM tests pin the manufacturer boundary.

**The widget fills its space and can be dragged again, under D-041.** Reported
from a phone: a lot of blank space at the bottom where a row would clearly fit,
and resize handles that did nothing.

**Both were one root and it had survived a decision written to prevent it.**
D-036 replaced the breakpoint table with arithmetic over the reported height,
arguing that a dragged widget is rarely either declared size. It kept the two
`DpSize`s because "Glance needs them to choose which RemoteViews it builds",
which is true and carries a consequence nobody traced: under
`SizeMode.Responsive`, `LocalSize` also reports the matched declared size, so
`heightDp` arrived as 190 or 266 and never anything else. The division was real
and its input was one of two constants. At 266 the model computes three rows no
matter how tall the widget is, which is the reported blank space exactly.
`SizeMode.Exact` now reports the real size, and the `DpSize`s are gone.

**The resize ceiling was never a decision at all.** `maxResizeWidth="364dp"` and
`maxResizeHeight="266dp"` are `MediumSize` to the pixel: the largest frame the
board drew became the largest size the widget was allowed to be, and no document
argues for a maximum. With `minResize` equal to `min`, the whole band was 76dp
in each axis, about one grid cell, so the launcher offered handles with nowhere
to go. The maximums are gone, `minResizeHeight` is 140 and `minResizeWidth` is
250, both sized to the row rather than copied from a frame.

**The tests could not have caught it, which is the part worth remembering.**
`FocuslistWidgetModelTest` pinned eight cases at 190 and 266, the two values
production was trapped at. A suite that only asks about the heights the code can
actually receive will pass forever. It now has a ladder at heights no breakpoint
could produce, and its fixture takes a task count, because six filler tasks made
a tall widget look capped by the arithmetic when it was capped by the work.

**Checked against TickTick**, by pulling its APK off the emulator, the same
method D-030 used. Its scrolling task list declares `minResize` 110x110 against a
294x180 placement and no maximum at all, so ours was the outlier. Not adopted:
their list is a `RemoteViewsService` collection that scrolls, so it never
measures and needs no `+N more`. That is sound and it is not this design, which
D-031 built around disclosing what did not fit.

Verified: 667 unit tests, and the installed provider now reads `minResize
250x140` with `resizeMode=3` and no maximum. **Not verified by eye:** nobody has
watched a widget grow a row while being dragged. That is the one thing the tests
cannot answer and it wants a real launcher.

**Reminders are grouped, and the two platform calls it looked like found two
bugs.** D-039. Every reminder carries `setGroup` and a summary counts them, which
closes the other frame the board drew and the app had never built. The count is
allowed under D-031's carve-out, disclosing what a collapsed stack hides, and it
counts notifications on screen rather than reminders fired or tasks outstanding.
The board's "Catimo · 3 reminders" lost its app name, because Android draws
that in the header and the string would have said Catimo twice.

**The first bug would have shipped and would have been severe.** The summary was
cancelled once fewer than two reminders remained, which is the obvious rule and
is wrong: the notification service cancels a group's children along with its
summary, so dealing with one of two reminders silently removed the other from the
shade. The user is not told about work they never touched, which `CLAUDE.md`
ranks above a crash, and it is invisible until a second reminder exists and the
first is handled. The summary now goes only when the group is empty.

**The second was a race, and it had already produced a wrong number.** `notify`
and `cancel` are processed on the notification service's handler, so reading the
count straight back sees a stale shade; it was observed announcing "1 reminder"
over two. The count is read from the shade and then corrected by the one id the
caller just moved.

**One cost is open rather than solved.** Grouping doubles the enqueue rate, two
posts per reminder, and the service sheds posts from a package exceeding a few
per second. Reminders due in the same minute produce that burst. A shed summary
is cosmetic; a shed reminder is not. D-039 records it, and the fix is coalescing
a burst rather than anything in this change.

Verified: `ReminderGroupTest`, 7 tests, three consecutive green runs on the
Pixel 10 emulator with `ANDROID_SERIAL` pinned, and 664 unit tests. Both bugs
were found by that suite rather than by reading, and the first version of it
passed against a shade that never received anything, because `postReminder` does
not create its channel and its callers do. That is the D-026 rule again: check
that a probe can fail.

Not verified by eye: no grouped stack has been looked at in a real shade.

**Today has a reminder health banner, and D-040 is the entry.** Reported as a
banner that would not appear with notifications switched off. It had never been
built: `TodayScreen` read no health state, and `ReminderHealthState` had exactly
one consumer in the app, the health screen. What made it look like a regression
is that `expressive-motion.md` carried two entries about the banner's motion,
including a paragraph arguing that it must not slide in because that would be
"the app performing its own bad news". The reasoning had been written down and
the component never had.

It draws for `ActionNeeded` and `Missed`, and never for `WorthChecking`. That
line is D-021's and it matters more here than on the health screen: a
`WorthChecking` banner fires from `Build.MANUFACTURER` alone, so it would sit
permanently on the default screen of every OnePlus, OPPO, Realme, Xiaomi, Redmi,
POCO, Samsung, Huawei and Honor, unclearable by anything the owner does. A label,
one sentence and a chevron, all of it in the health screen's own strings, opening
the health screen. At the time it could not be dismissed; D-044 later supersedes
that rule for `Missed` only, while keeping `ActionNeeded` non-dismissible.

This is Phase 2 reliability work landing after Phase 5 design work, which is out
of order and was asked for directly. D-029 is not superseded: it removed Reminder
health from the overflow partly on the grounds that "contextual warnings and
reminder deep links may take a user directly to the health screen when action is
needed", and no such warning existed at the time. This is that warning, so the
entry point D-029 traded away now has its replacement.

`FocuslistNavHost` owns one `ReminderHealthViewModel` above the graph now, handed
to both Today and the health screen, and refreshes it from a
`LifecycleResumeEffect` on the Today destination. Permissions change while the
user is in Settings and Android offers nothing to observe, so without the re-read
the banner would outlive the problem it names.

Verified: `TodayScreenSemanticsTest` passes on the Pixel 10 emulator with
`ANDROID_SERIAL` pinned, 25 tests, 11 of them new. They cover the sentence naming
the cause rather than the category, the tap reaching Reminder health, the banner
surviving the empty screen, and its absence for `WorthChecking`, `Ready` and
`Checking`. `NavigationSemanticsTest` still passes, 14 tests, which is what says
the hoisted view model did not disturb the graph.

Not verified by eye: the banner has not been seen rendered, in either theme or at
either font scale. `errorContainer` above the segmented collection is the one
pairing on this screen that has never been looked at together.

**The full-screen alarm is dated rather than cut, under D-038.** It ships after
1.0. The board has drawn `notify/Full screen alarm` since Phase 1 and no
`setFullScreenIntent` has ever existed in the app, which is the state that makes
a thing look built to a session that has not checked.

Three things decided it. The warranting rule has to be per-task, because an app
that picks which of the user's tasks may take over their device is making the
claim D-031 refuses on the widget; per-task means schema version 11, a backup
format change, a Task Details row and a lock-screen activity with its own
permission flow, so it is a phase rather than a Phase 5 bullet. Threading a
second delivery path through the reminder pipeline immediately before the first
release inverts the sequencing the roadmap rests on, and `CLAUDE.md` puts a
reminder that does not fire above a crash. And Play's declaration is worst spent
on a first submission, where a questioned claim holds the launch rather than
costing a feature.

**Checked rather than recalled, which changed the shape of the entry.** TickTick
8.1.3.6 on the emulator, installed by `com.android.vending`, declares
`USE_FULL_SCREEN_INTENT` and holds it `granted=true` at `targetSdk=37`, the same
target Catimo builds against. So the permission is obtainable by a task app
rather than reserved to alarm clocks, and D-038 defers on sequencing alone
rather than on a policy wall that is not there. The dump cannot say whether Play
pre-granted it or the user did; that is recorded as a lead.

The failure mode is also a floor rather than a cliff: since 22 January 2025 a
non-qualifying app is not refused the permission, only denied the pre-grant, and
degrades to the heads-up notification the app already posts. Nothing about
waiting makes the eventual build harder.

`reminders.md` no longer says "not built" without saying why, and the item heads
the After 1.0 list as the first thing to reconsider. **Grouping was deliberately
not folded in.** It is the other unbuilt frame, it is far smaller, `setGroup` and
`setGroupSummary` with no schema change and no policy question, and it wants its
own answer rather than being carried along by this one.

**Task Details has a floating toolbar, and D-037 is the entry.** Start focus and
Delete are one `HorizontalFloatingToolbar` pinned bottom centre, which retires
both the full-width button at the foot of the scroll and the app-bar overflow.
What started it is that an overflow whose only item is Delete promises options it
does not have: three dots say there are more of these, and there was one. D-022
argued well against an icon button, against a button beside Start focus and
against a confirmation dialog, and never against the menu being a menu of one.

The order inverts the row menu's rule and applies its reasoning. In a vertical
menu the thumb lands nearest the bottom, so constructive goes before
destructive; in a horizontal bar it lands nearest the reaching side, so Start
focus trails and Delete leads. That is
`FloatingToolbarHorizontalFabPosition`'s own default, not an arrangement imposed
on the component.

The honest cost is that Start focus loses its label, and `task-details.md` had
recorded that the same button without its glyph "was pressed by people meaning
to close the page". A small play icon in a floating bar is not open to being read
as a commit control, so that specific trap is gone; what replaces it is lower
discoverability, and D-037 is the entry to supersede if it costs more than the
overflow did.

`expressive-components.md` lists `FloatingToolbar` under "Do not introduce,
unless a later product decision explicitly requires one". This is that decision.
The same list still forbids `ButtonGroup`, which D-026 introduced through the
same escape hatch and nobody struck off; that line wants correcting either way.

**There were two D-036s, and the second is now D-037.** The widget's capacity
entry and this one were both written as D-036. The widget keeps the number,
since its code and tests already cite it; Task Details takes D-037 across
`decisions.md`, `task-details.md`, `TaskDetailsScreen.kt` and
`TaskDetailsSemanticsTest.kt`.

Verified: `TaskDetailsSemanticsTest` passes on the Pixel 10 emulator with
`ANDROID_SERIAL` pinned, 14 tests. Both actions are found by content description
and displayed at 100% and 200%, which is what says the pinned toolbar has not
been pushed off screen at the larger scale, and Delete on the final task still
pops the screen now that it is a toolbar button rather than a menu item.

Not verified by eye: the toolbar has not been seen rendered. Whether a small
play glyph reads as Start focus to someone meeting the screen for the first time
is the thing the tests cannot answer, and it is the cost D-037 accepts.

**The board redrew four poses, and the code took the redraw without moving.**
The sleeping, sitting, lying and question cats were replaced rather than edited,
so every path changed and each took a new node id. Nothing else had to change:
part names, paint order and the three colour bindings all survived, and the drawn
sizes moved by under half a dp at the shared 0.36430 scale. That is the property
`MascotImage` was built for, and this is the first time it has been tested by an
actual redraw rather than by a swap of the whole set.

The scale factor stays anchored to the numbers the first Today pose set,
210.66 / 578.259. Re-deriving it from the redrawn Today would have moved all five
poses to chase a change of a fortieth of a dp, so the constant is the constant and
Today now draws at 210.67x130.49 rather than 210.66x130.58.

**The widget's lead card had escaped its own container.** Reported from a phone:
the card's surface ran edge to edge, touching both sides of the widget. Glance
takes `padding` as a view's own padding rather than as a margin, so
`.padding(horizontal = 8.dp).background(surface)` put the 8dp inside the card and
drew the surface across the full width. A parent Box carries the inset now, which
is how Glance expresses a margin, and the card's contents did not have to move
because they were already written against an 8dp offset.

The alignment pass around it found the reason this was easy to miss: the widget
had no named geometry. The insets were 18 in the header, 18 in the empty states,
8 on a row, 24 at the end of one, and 58 on the disclosure, so nothing lined up
with anything and each element picked whichever number was nearest in the file.
There are two constants now, `ContentInset` at 24 and `SurfaceInset` at 8, and
`widget.md` has the section explaining which takes which and why the checkbox
glyph is allowed to land one dp inside the text column.

Verified on the emulator with a real 4x3 widget and a real paused session: the
card is inset, and its checkbox, title and Resume pill line up with the row
beneath it.

**The widget now fills the space it was given, and D-036 has the argument.**
`rowCapacity` was a seven-branch `when` over a `WidgetLayout` enum resolved by
comparing both dimensions against the Medium breakpoint, which treated the two
declared responsive sizes as the only two shapes a widget can have. Widgets are
resized by dragging, so most are neither: one taller than Compact and slightly
narrower than Medium fell to Compact, and Compact with a lead card is zero rows,
which is why a large widget drew one card over an empty container. Width had a
vote purely because it shared a breakpoint with height.

Capacity is arithmetic over the reported height now, with the composables'
heights named as constants beside it and text scale multiplying the row and the
card rather than tripping a `largeText` threshold. The enum is gone. The two
`DpSize`s stay, because Glance needs them to pick which RemoteViews it builds.

It reproduces the old table exactly at both declared sizes across all eight
combinations, and `FocuslistWidgetModelTest` pins those eight so any change to a
height constant has to say which case it moved. The reported bug has its own
test at 320dp.

Not verified by eye: the emulator's widget reports close to the Medium height, so
it renders the same one row either way, and a resize to an in-between height did
not take. The improvement is proven by test, not by a launcher.

**The "widget does not refresh on a session change" report was wrong, and the
record is corrected here.** `keepWidgetInStepWithState` already combines
`focusSessionStore.changes` with `observeTasks()`, `onCreate` starts it, and
`writeFocusSession` emits on every save and clear. Verified on the emulator:
resuming a paused session removed the lead card from the widget within seconds
and put its task back in the rows. Nothing to fix, and nothing was changed.

What produced the original report was a session that was still *running* while it
was assumed to be paused, and then a completion suppressing the lead by design.
One observation is still unexplained: immediately after an install, a session
paused before the install did not appear until the next task write. The suspect
is the `drop(1)` in that flow, which exists so a Glance `SessionWorker` that
created the Application does not cancel itself, and which would also drop the
first emission when the process is new. That is a hypothesis, not a finding, and
the comment it would touch describes a real failure it prevents, so it wants
isolating before anything moves.

**One flaky unit test, fixed.**
`TaskListViewModelTest.undoAfterASecondDeletionRestoresTheSecondTask` failed once
and passed on clean re-runs. Its last line read `todayTasks.value` straight after
`awaitRestore()`, which waits on the DAO while the derived stream emits after
that, so the read could land in the gap. It goes through `awaitTodayIds` now,
which the file already had for exactly this and which is bounded, so a wrong list
still fails the assertion rather than hanging the run.

**A finished backup or restore says so, and nothing was saying it.** Reported
from use: restoring a file gave no feedback at all. `BackupViewModel` landed
success on `BackupUiState()`, which is the state the screen starts in, so the
picker closed and nothing else happened. The Backup page shows no tasks, so a
restore that had replaced the whole database looked exactly like one that had not
run. Export was identically silent for the same reason.

Both now report through a counted snackbar: "Restored 47 tasks". A count rather
than a bare confirmation, because it can be checked against what the lists hold a
moment later. Soft-deleted rows are excluded, since a backup carries them but no
list shows them. `BackupError` became `BackupOperation`, one enum naming which
half ran rather than only how it can fail, and `BackupDone` carries an id so that
restoring the same file twice announces itself twice.

`settings.md` had specified the restore error dialog down to its copy and never
specified success at all, which is how this survived review. That section exists
now, along with a note that in-progress state is deliberately nothing but two
disabled buttons.

Not verified by eye: the snackbar has not been seen on a device. Restore's
reminder rescheduling was checked and is sound, `keepRemindersInStepWithStorage`
reconciles on the write.

**The Focus now card stopped guessing, and D-035 has the argument.** It had
three reasons and one of them, `NoTimeToday`, matched any task scheduled for
today carrying no time, which on an ordinary day is most of them. Under it the
card was the first row of the "No time set" band lifted out and drawn larger,
explained by that band's own label, on grounds equally true of every other row in
the band. That is a ranking with nothing to say why its head is its head, which
is what D-004 removed the queue for.

The reason is gone from the enum, so the card now speaks only for a paused
session or a reminder that fired and was not acted on. Both are events; neither
can be read off a list. The knock-on is that D-031's widget threshold is no
longer a threshold: the filter came out of `FocuslistWidgetModel` because the
rule it was correcting no longer produces the reason, and `focusNow` lost its
`today` parameter because nothing left in it asks what day it is.

**Today's ordinary state is now a screen with no card**, which is the cost taken
knowingly: a light user who sets no reminder times may rarely see one, and the
card is the only one-tap route from Today into a focus session. Previews and
review passes should treat the cardless screen as the default form.

Verified: the full unit suite passes, and `TodayScreenSemanticsTest` and
`FocusSessionSemanticsTest` pass on the emulator, 24 tests at both font scales.
The card and Today previews were updated to a passed reminder, since the old
fixtures no longer produce a card, but have not been looked at rendered.

**The board's empty and task-list error states now use the current mascot set.**
The illustrated component variants in section 08 use the prop-free sleeping,
sitting and lying cats, so every linked instance stays in step. Sections 13 and
14 use the question-mark cat for failed task reads while Logbook remains
illustration-free, preserving the distinction recorded in
`expressive-components.md`.

**A failed read now says so, and it turned out nothing was catching one.**
`expressive-components.md` described the error empty state in full, down to the
copy, as though it were built. None of it existed: the component took a headline
and a supporting line, there were no error strings, and `TaskListViewModel` had
no error path at all. Worse, each of the five list views subscribed to
`observeTasks()` separately, and a Room `Flow` that throws is finished, so a
failed read would have left every screen sitting at its initial empty list.
Today would have said "Nothing scheduled" on a full day, which is the app
asserting that work does not exist. The read is caught once now, every view
derives from the caught stream, and Try again starts a fresh read rather than
resuming a dead one. D-034.

**Today gained a finished-day state, and the first version of it was wrong.**
It drew in place of the whole body like the other empty states, which deleted
the Completed disclosure. `undo_reopensTheTask` caught that, and the test was
encoding something real: once the undo snackbar times out, that disclosure is
how a task completed today is reopened, and D-012 built it for exactly that. It
heads the list now, with the rows beneath it. `TaskListDoneHeader` and
`TaskListEmptyState` share one body composable so the two forms cannot drift.
D-033 supersedes the claim in `widget.md` that Today needs only one empty state.

**Two more mascot poses, and the set is five.** `cat-question` for a failed read
and `cat-sitting-happy` for a finished day, both from the board at the same
0.36430 scale factor the first three share, landing at 173x170 and 195x176. The
question mark is the only prop in the set and has to be: a posture can say why a
list is empty, but not that a read failed. The happy cat is a sitting pose like
the Inbox one and differs in the eyes, closed against open, and takes the same
lighter nose that sitting poses already had an exception for.

**The error state took the mascot, so the headline carries the colour.** The doc
had specified an 80dp `errorContainer` icon and called it, with the button, "the
signal". By the time the state was built the illustrated variant existed, and a
mascot draws in the fixed primary tones and says nothing about severity. The
headline takes the error role instead. The copy still never mentions a
connection, since every read here is local.

**Today's empty supporting line stopped instructing.** It read "Add a task when
you are ready", the only one of the four aimed at the user rather than at the
screen, and it sat under a cat drawn asleep. It states a fact now and points at
the Inbox. The rule that a supporting line never instructs is written down,
along with why "Nothing overdue, either" was tried and is wrong: overdue is one
of Today's own bands, so the blank screen already proves it.

Not verified on a device: nothing is attached, so the instrumented suite has not
run and none of the five states has been seen rendered. The finished-day header
inside the list and the error state at 200% font scale are the two worth looking
at first.

**The live widget row now matches the board rather than only its picker
preview.** Launcher inspection exposed the difference: Glance's native
RemoteViews checkbox does not carry Material's horizontal inset, while the
unconditional 7dp vertical correction pushed it below a title carrying
metadata. Rows now begin at the board's 8dp inset, place the glyph 15dp inside
its unchanged 48dp target, and apply the vertical correction only to one-line
rows. They also reserve the board's 70dp duration column plus 24dp trailing
space. Duration and metadata were confirmed in the live widget from stored task
fields.

**Deleting the last task no longer strands Task Details on a blank route.**
The screen now remembers that its task was successfully shown, so that task's
later disappearance is conclusive even when `allTasks` becomes empty. It still
does not mistake the flow's initial empty placeholder for a deleted task, and
an instrumented regression test covers the final-task case.

**The Phase 4 home widget is built.** One responsive Jetpack Glance provider
implements the board's Compact and Medium states at 288x190dp and 364x266dp:
ordinary rows, reminder-passed and paused leads, just completed, everything
done, and nothing scheduled. It follows system dynamic colour, falls back to
the app palettes before API 31, uses launcher corner dimensions where they
exist, and includes the exact Medium board frame as its picker preview.

The consultation closed D-031's two product questions. The add button remains
at Compact for direct capture. A just-completed row remains where it was, and
both its checkbox and row body undo through `TaskCompletion.reopen`, including
safe cleanup of an untouched recurring occurrence. The two responsive
breakpoints use the smallest complete board layouts; API 29 and 30 retain the
declared dp size and corner fallbacks but still need visual verification on an
older launcher.

Room writes, date/time/timezone changes, and persisted Focus-session changes
refresh every widget without polling. Persisting the Focus task id alongside
its clock also repairs the existing process-death promise: a paused session can
still be resumed after the app process has gone away, which is necessary for
the widget's Resume action. Widget refreshes are conflated without cancelling
an update already in flight, preventing rapid Room emissions from leaving the
launcher on stale task data. Room and Focus signals use one serialized update
path, and its initial Room value is skipped so creation of a Glance worker does
not recursively enqueue and cancel that same worker. The add control keeps its 48dp touch target while
using a 32dp visible circle aligned with the Today title. Dynamic checkbox
roles are resolved before they enter Glance's checked/unchecked API, fixing the
runtime failure that previously left the widget empty as soon as it had a task
row to render. The native checkbox glyph carries Material's horizontal optical
inset without reducing the 48dp target. The pure widget model covers state selection,
capacity, completion evidence, urgency and large font scaling with JVM tests;
manifest tests cover the provider and closed-app refresh wiring.

**The three lists have their mascots, and Catimo has a cat.** The empty
states had been two lines of text on an otherwise blank screen. Each now draws
the same cat in a different posture, and the posture is what says why the screen
is empty: curled asleep for a day with nothing on it, sitting upright for an
inbox waiting to be filled, lying down but awake for days that are still clear.
No copy changed anywhere; `strings.xml` already held all six lines exactly as the
board writes them. The app icon is the same cat, face on.

**The mascot changed three times in a day, and that is the useful finding.** It
was a dachshund, then a cat with a prop on each screen, then this. Every swap
cost three generated files and one KDoc, because the poses are data,
`MascotImage` owns the colour roles and the sizing, and a screen asks for a
mascot without knowing what is inside it. Nothing about the screens, the tests or
the empty-state component moved on any of the three.

Each is an `ImageVector` built in Kotlin rather than a vector drawable, because
their fills are colour roles and a drawable can neither read the Compose colour
scheme nor take three colours from one tint. All three use the same three fixed
roles `Color.kt` set aside for this: `primaryFixed` for the coat and the soft
shade it sits on, `primaryFixedDim` for the shading that gives the coat its
folds and its tail, `onPrimaryFixedVariant` for the eyes and nose. They bind by
the part's name rather than by its colour, since the greys drift between poses
and the sitting cat's nose is deliberately drawn lighter than its eyes.

The three share one scale factor rather than a common width or height. A cat
sitting is genuinely taller than the same cat lying down, so matching either
dimension makes them read as two differently sized animals; the board already
drew them at a consistent scale, within about 9% by area, so carrying that scale
through is the whole rule. They land at 211x131, 190x173 and 250x134.

Rendered under a device palette rather than the app's own, the illustrations
hold: the tones are fixed points on one tonal palette, so a wallpaper changes
the hue and nothing else. That is the argument for leaving them themeable while
the launcher icon stays fixed hex, since a launcher draws outside the app's
theme and an icon has to be recognisable among strangers.

The headline stayed at `titleMediumEmphasized`. The board draws it at Title
Large Emphasized, which is what the app bar uses, and two identical headings
240dp apart on an otherwise empty screen leaves neither leading;
`expressive-components.md` records the divergence so the next session does not
rediscover it. What a mascot does change is how much height the copy has to wrap
into, so `EmptyStateSemanticsTest` covers all three poses at both font scales
and asserts that none of them is ever announced. Upcoming's is the one with the
least room, at 167dp against Today's 102.

Verified by rendering all three screens on the emulator in light and dark, and
Upcoming at 200%. Lint is unchanged.

**The widget design pass established D-031 before implementation began.**
Phase 4's remaining item had six board frames and no design document, which is
how it drifted from the app without anyone noticing. It is specified now.

The rule is that a surface nobody asked to look at needs a higher bar to speak
up than a screen deliberately opened. So the widget leads with a single task and
its reason for `ResumePaused` and `ReminderPassed` only. `NoTimeToday` matches
most tasks on an ordinary day, and leading on it would have the widget asserting
all day about work whose only claim is that the user put it on today. It gains
two quiet end states, *everything done* and *nothing scheduled*, which Today does
not need because its Completed section sits on the same screen. A checked row
stays in place until the next refresh rather than vanishing, because on a home
screen a mis-tap otherwise erases its own evidence. "3 tasks left" is gone, and a
count now discloses only what did not fit. Size changes how many rows fit and
nothing else.

**Dynamic colour settled something that would otherwise have gone to taste.**
`Tertiary` on `Primary Container` measures 4.99:1 in Light, 5.51:1 in Dark and
3.53:1 in Wallpaper warm, which is below AA for a 12sp line, and `Error` is no
better at 3.61:1. Reducing opacity makes it worse rather than better. So the
widget spends no colour on meaning: overdue reads from the date, and row
hierarchy is 12sp Regular against 14sp Medium. Nothing about that failure is
widget-specific, so the same pairing is worth measuring in sections 16 and 18.

**Board section 10 is twelve frames, up from six.** The ordinary state keeps its
three colour treatments and gained metadata lines, an overflow hint and a
duration-only trailing column. Six new frames draw what the decision requires and
the board had never shown: the reminder-passed and paused leads at Medium, the
lead alone at Compact, everything done, nothing scheduled, and a just-completed
row still sitting where it was. Growing the section to two rows moved sections 11
through 18 down 980px, because the page is one stacked column on a fixed 120px
rhythm and no section can grow locally.

**At that point no widget code existed.** There was no Glance dependency,
`appwidget` receiver, or `ui/widget` package. `docs/design/widget.md` carried
four questions into implementation: responsive sizing, the API 29 and 30
corner and size behaviour, whether the add button survived at the smaller size,
and whether a checked row could be tapped to undo. The current entry above
records how those landed.

**Reminders no longer promise moments that have already gone, under D-030.**
Two reminders on the OnePlus 8T were recorded as placed at 03:20 and 11:00 and
delivered five seconds later. That read as OxygenOS delaying an alarm and was
not: the alarms were placed correctly and were already overdue when placed. The
reminder control selected a time and took its day from the task, so a task
scheduled for a day past could only take a reminder on that past day, which
`reminderTrigger` then clamped to now. `PRODUCT.md` says a reminder is
independent of a scheduled date, and a control with no date could not say so.

`nextReminderOccurrence` puts a time of day on the first day it is still ahead,
and both doors route through it. Quick Add was the second one and was missed at
first: "call the dentist at 3pm" typed at 4pm captured a reminder an hour gone.
`date-parsing.md` already promised no supported input resolves to the past, and
that promise covered the day peel only.

`ReminderDialog` becomes `ReminderSheet`: a Day row and a Time row, each opening
its own picker, then Save. The first build put day presets inside the time
dialog and was rejected on density. A day the sheet worked out resolves forward
and is shown; a day the user chose is honoured, and a moment already gone
disables Save under a line saying why. The scheduler's clamp is untouched, since
it serves reminders that were correct when set.

Checked against TickTick on the emulator, which resolves a typed past time to
tomorrow before saving and warns after the fact on an explicit one. The forward
resolution and the single control were taken; the warn-and-store was not.
Verified on the emulator end to end: a task scheduled Sep 3, edited at 19:04,
now schedules `RTC_WAKEUP 2026-09-09 09:00`. Ten instrumented tests cover the
sheet and three of them fail against the old behaviour.

**Confirmed on the 8T itself.** Its `reminder_deliveries` rows read
`scheduledAheadMs = 0` on both bad reminders, which is the number that rules the
manufacturer out: the alarms were already overdue when placed. `POST_NOTIFICATIONS`
is granted there, correcting an earlier reading taken from the emulator. A real
task whose reminder was Today 00:00 now resolves to Tomorrow and schedules at
`+4h35m` instead of zero.

**No new reliability code was needed, and one connection was found.** Reminder
health already measures lateness against the moment an alarm was aimed at, so it
never blamed the phone for these, and already refuses a zero-lead delivery as
evidence. That second rule is why the `SleepStandby` warning could never clear:
both recorded deliveries had zero lead, so the check had nothing to learn from.
D-030 is what lets it start gathering evidence. Re-confirmed on the device that
the OxygenOS demotion survives the Doze allowlist, exactly as D-009 recorded.

**Settings and local continuity were pulled forward under D-028.** D-024's list
is now the implementation: Reminder health, Dynamic color, Theme, and Backup &
restore, in three sections and no fifth row. Settings is a room in the app-bar
overflow, the appearance choices persist and drive `FocuslistTheme`, and Theme
commits from its three-radio dialog without an extra confirmation step.

Backup & restore uses the Storage Access Framework, so the user chooses the
file and Catimo never invents a cloud destination. Its versioned JSON carries
every stored task field and both appearance settings. A restore validates the
whole file before one row changes, then replaces the task set in a Room
transaction. Reminder delivery history is cleared because it measures the old
phone rather than the tasks and would make Reminder health lie on a new one.

No dependency moved with the feature: Android's `SharedPreferences`,
`JsonReader`/`JsonWriter`, and document contracts already supply the required
pieces. The debug APK builds, the full JVM suite passes, and focused emulator
tests cover the codec, invalid and future files, database replacement, Settings
interactions, and navigation. The Settings and Backup pages were inspected on a
Pixel 10 emulator against board node 161:3409. The Phase 4 widget is still not
done, and nothing else from Phase 5 moved.

**Reminder health now has one home under D-029.** Its Settings row remains the
permanent entry, while the identical app-bar overflow entry is gone. More now
contains only Logbook and Settings; contextual reliability warnings may still
open Reminder health directly when there is something to act on.

**Lint passes again.** Notification posting now checks permission at the point
of use and handles a grant being revoked between that check and `notify()`. Real
and test reminders use the result when recording delivery, so a rejected post
is `Suppressed`, remains owed, and is never reported as `Announced`. The debug
seed also uses `LocalDate.ofEpochDay(0)` instead of the newer `LocalDate.EPOCH`
field, preserving the same date on the app's minimum API level.

---

**Recurrence is the board's editor now, and `docs/decisions.md` D-027 is why it
was allowed to be.** D-019 held the nine Repeat frames as a Phase 4 design and
out of the code, on the argument that "a schema change and a rule engine
arriving through a Figma frame" is what the drift guard exists to catch. That is
an argument about how a change arrives rather than whether it should, and D-019
said as much in its own closing line. Pulling it forward is a decision written
down before any code moved, with the schema enumerated and the counting problem
answered rather than discovered.

**`Recurrence` stops being a four-value enum and becomes a rule**: a
`RecurrenceUnit`, an interval, a weekday set, and a `RecurrenceEnd` of Never, On
date or After occurrences. Schema version 10 appends five columns and rewrites
no row: the `recurrence` column keeps its name and its four stored values, so an
install that has been repeating a task since version 3 carries on repeating it.
Verified on a device, where the seeded monthly task still reads "Monthly" after
the upgrade.

**D-019 named the thing that had to exist first, and it was right.** An end date
and an occurrence count both need to know how many occurrences have happened,
and nothing recorded it. The chain of `spawnedFromId` links looks like the answer
and is not: counting it costs a walk on every completion and deleting any one
copy breaks the count silently. `Task.occurrenceNumber` records the position
directly, the spawned copy takes its parent's plus one, and existing rows default
to 1, which is the conservative reading rather than a guess.

**Weeks are counted from Monday, and that is a fixed reference rather than a
display convention.** "Every two weeks on Monday and Thursday" has to agree with
itself about which weeks are on-weeks, and a locale-dependent week start would
move the rule when the locale changed. An empty weekday set means the anchor's
own weekday, so clearing every chip cannot leave a rule that matches no day at
all.

**The sheet is three panes in one `ModalBottomSheet`**, on the shape D-026
settled for Duration. It is also the one sheet on Task Details with a Save,
because a rule is four fields that only mean something together and writing each
tap would put half-built rules on the task, each one rescheduling the series.

**The board does not draw a way to stop a task repeating**, which is the one
thing its eighteen frames leave unanswered. The sheet adds "Doesn't repeat"
beside Save, shown only while the task repeats, on the reasoning the date sheets
already use.

**Two defects were found by looking rather than by testing.** The seven weekday
chips at their natural width came to 1002px inside a 992px column, so the last
day wrapped to a line of its own at the default font scale;
`FocuslistDimensions.WeekdayChipSize` fixes the width at the board's own 48dp and
floors the height there. And `occurrenceNumber` had its default only in the
migration, so
the table Room creates on a fresh install had `NOT NULL` with nothing to fall
back on and the debug seed's insert failed. `@ColumnInfo(defaultValue = "1")` is
what makes the created table and the migrated one agree. The second was caught
by the instrumented suite; the first only by opening the sheet.

**Verified.** 635 unit tests pass, 47 of them on recurrence. On the emulator with
`ANDROID_SERIAL` pinned: `MigrationTest`, `TaskDaoTest` and
`TaskDetailsSemanticsTest`, 52 tests. `repeatPicksAPeriodAndOffersNoEditor`
asserted that none of Every, Days or Ends appeared; the assertion is inverted
rather than deleted, because those three names are what tells the two designs
apart. End to end on the device: picking Wednesday and Friday, saving, completing
Tuesday's copy, and watching the next occurrence land under Tomorrow.

**Two more board mismatches turned up when the chips were questioned, and the
board's own node names settled both.** `Weekday selector / 7 equal 48dp targets`
gives the size, so 40dp became 48dp with `SpaceBetween` spending the remainder.
And the frame around it is named "require >=1 selected day", which nothing
enforced: the last selected chip now does not come off, while `Recurrence` keeps
reading an empty set as the anchor's weekday, because every weekly rule written
before version 10 has one.

**The chips are `FilterChip`s, and that is a choice rather than an oversight.**
`Chip.kt` carries no reference to Expressive; the expressive control for picking
several from a short set is the connected button group. The board does not draw
one. Its weekday node is seven separate instances while the modal sheet, the app
bar and Save repeat in the same frame are all named as `M3` instances, and
`ButtonGroup` would overflow a day into a dropdown when it ran out of room.

**The Focus now card takes a tap now, and the bug was in a code comment rather
than in the code.** `FocusNowCard` said "the card is not itself a button ... a
clickable container behind two controls is a third target the user cannot see
the edges of", which is a fair worry that answers the wrong question. D-012 takes
the promoted task out of the bands below, so an unclickable card left that one
task's details unreachable from Today: not in a band, not in Upcoming, which
holds later days, not in Inbox, which holds undated work. The task the screen is
built around was the only one on it that could not be opened.

Worth noticing that the behaviour was decided in a KDoc and never reached
`today-screen.md`, whose interaction table listed rows and said nothing about the
card. A rule that removes a row has to account for what the row could do, and the
place to check that is the document, not the comment.

That table also still described Task Details as a sheet with a Save and "no
details screen or back stack", which D-018 replaced. Corrected while the table
was open, because adding "opens task details" directly above it would have made
the contradiction worse.

**The Repeat sheet gained a summary line under its title**, which the board added
and which is the only place the whole rule is stated: "Every week on Mon, Wed and
Fri, until Aug 17", read off the draft rather than the task. It earns its place
on the compound rules, where the controls alone cannot say whether "every 2
weeks" with three days lit means alternating Mondays or alternating weeks, and
rides along on the simple ones, because a summary that appears only when things
get complicated is one nobody reads when it does.

The rows stay compact against the board, which draws them carrying the same
phrase. `RecurrenceStyle` replaces the boolean that told the notification from
the screens: Compact for the rows, Sentence for the notification, Full for the
subtitle. Weekday lists now go through `android.icu.text.ListFormatter`, so the
conjunction is the locale's rather than a rule invented for English; an en-US
device reads "Mon, Wed, and Fri" where the board, drawn in en-GB, reads "Mon, Wed
and Fri", and both are right.

`RecurrenceTextTest` pins the three styles apart by their relationships rather
than by their words, because a test that spelled out what ICU produces would fail
on a device that was merely configured differently. That is the lesson the
locale-brittle chip assertion taught.

**The Plan row could be starved by its own value, and that was the worst of the
three.** `PlanRow` put the value in `ListItem`'s trailing slot, which is measured
before the headline, so with every weekday selected the Repeat label held 139px
at 100% and was not displayed at all at 200%: a row showing a value with no name,
to a screen reader as well as to the eye. Every row in the group was exposed to
it. It is one `Row` now, label measured first.

`PlanRowSemanticsTest` guards it, and the first version of that test was wrong in
the way D-026 predicts. `SegmentedListItem` merges its descendants, so asking the
default tree for the label and the value returned the same node: it reported
1080px for both and passed against the broken layout. Reading the unmerged tree
and comparing one label against another makes it fail on the old code at both
scales and pass on the new. Check that a probe can still fail.

**Two weekday sets are named rather than listed.** All seven days at an interval
of one is a daily rule, asserted across a fortnight, so the row says "Daily"
instead of spending its whole width on the list that started this. The weekend
and the working week are named too, and which days those are comes from ICU
rather than from Monday-to-Friday. Naming describes and never rewrites, so the
stored rule keeps the chips the user actually chose.

**A third chip defect was found by eye and then measured.** Material's
`ChipArrangement` places a chip's first child at x = 0 and spends the slack on
the right, which is right for a chip as wide as its content and wrong for one
given a fixed width. The letters sat left of the circles around them, 5px for M
and W and 10px for T, F and S, which is half the leftover space and so worst on
the narrowest glyphs. `Arrangement.Center` brings all seven within half a pixel.
Worth knowing that the component does not centre for itself.

**The chips were looked at on the emulator at 100% and 200%**, and 48dp is what
made the second one comfortable: at 40dp the M met the edge of its circle, and at
48dp every letter sits inside with margin at both scales. Observed rather than
tested. Nothing guards the number, and D-026 says how to retake a measurement
like this and why counting nodes is the wrong way.

**Not done.** Phase 4's Glance widget is untouched; recurrence is the half of it
that moved.

---

**Reminder health now has a design document**, which it had gone without while
every other screen had one. 875 lines of code, sixteen board frames, the area
`PRODUCT.md` principle 1 calls the highest severity in the product, and nothing
written down. Most of the reasoning already existed as KDoc and was lifted into
`docs/design/reminder-health.md` rather than invented.

Four board defects went with it. The header was hand-drawn in all fifteen frames
and is now `Room header`, which leaves no room in the app still drawing its own.
The check rows were three separate cards while every other list is a segmented
group, so `Check row` gained Position variants bound to the shared
`Segmented/*` variables. The eyebrows were literal capitals in the string values
and are now sentence case, on the board and in `strings.xml`. And `clone()`
silently dropped the component property references on the six new variants,
which made every check row render its default text until the references were
restored; worth knowing, because nothing warns you.

**That review produced D-021, which is the most consequential thing in this
pass.** `CheckState` had three values and the screen drew two: `Warning` and
`Blocked` shared a mark and an error tint. Since `DeviceRestriction` is inferred
from `Build.MANUFACTURER` and never measured, every OnePlus, OPPO, Realme,
Xiaomi, Redmi, POCO, Samsung, Huawei and Honor user saw a permanent red "Action
needed" from first launch, on a phone where nothing might be wrong. A
reliability screen that is always red teaches people to ignore it, which is the
one thing this screen cannot afford.

The rule now is that **the app colours what it knows.** `Blocked` keeps the
error container because the app was refused and can say so; `Warning` takes the
ordinary row surface and a question mark because it is a guess. The headline
follows: a device whose only problem is inferred reads "Worth checking" on a
neutral card, and the body says what the app cannot know, "Catimo cannot tell
whether it is on."

**Tertiary was tried first and rejected on the render**, which surfaced a
palette fact worth keeping: `tertiaryContainer` is `#FFD7E3` and `errorContainer`
is `#FFD8D6`, one step apart in green. The Catimo palette has three usable
container families, lavender, pink and neutral, because primary/secondary and
tertiary/error each collapse into one. That constrains any future state colour,
not just this screen's.

The board carries it, `strings.xml` carries the new label and sentence, and the
code still needs the three-way branch in `Badge()` and `detail()`.

**The check row badges went too.** Each row carried a 40dp circle behind its
glyph, and on a `Blocked` row that circle took the error container while the row
did as well, so it was invisible in the state that matters most. A container
that disappears in one of three states is not carrying anything. The glyph now
sits directly in a 40dp slot and takes the colour itself: `primary` for a tick,
`onSurfaceVariant` for a question mark, `onErrorContainer` for an exclamation,
which reads more strongly than it did behind a badge of its own colour.

**The longer Caution body overlapped the first check row by 8dp** in two frames,
because the headline grows with its copy and the rows sat at a fixed offset. All
eleven frames are now on a measured 12dp gap, and `reminder-health.md` says why
that gap is worth stating.

**D-023 removed the task row's actions menu, and the trailing button with it.**
The board's `Task row` has always been checkbox, title, metadata, duration; the
code had added a trailing button the board never drew. Checking why exposed a
premise that three later decisions had quietly removed.

`expressive-components.md` justified the button by saying Delete and Focus "live
only in the actions menu, Task Details deliberately excludes both". D-018 gave
Task Details a Start focus, D-022 gave it Delete, and its Plan rows give it
rescheduling. Nothing was excluded any more, and the button had outlived its
reason without anyone going back to check.

The menu had also become a worse duplicate: Today, Tomorrow and Pick a date
against the Scheduled sheet's No date, Today, Tomorrow, This weekend and Choose
a date. Three of its five items were rescheduling, which is administration, and
a permanent trailing button gave the most administrative action the most
prominent position on every line of every list.

The cost is a tap. Rescheduling from a list is three rather than two, and rows no
longer answer a long press. Nothing became unreachable. If it proves too slow the
answer is a bottom sheet on long press, which is where Material's compact
guidance points for a five-item menu, not the button returning.

`TaskListRow` went from 266 lines to 108. Four call sites lost their
`onDelete`, `onReschedule` and `onFocus` arguments, four instrumented tests that
asserted the menu were deleted, and two now-dead semantics fixtures went with
them. Seven design documents carried references to the menu and all seven are
updated.

**One simplification fell out.** `focus.md` had three entries and the row
long-press was the one that skipped Ready and started a session directly. With it
gone, every entry lands on Ready, so the state a user meets no longer depends on
how they arrived.

**This also deleted most of the menu pass that prompted it.** The remaining
menus are the app-bar overflow, three destinations of one kind, and Task Details'
overflow, one item. Neither needs the Expressive gap treatment, so the grouping
work is moot. What is left is componentising the More menu on the board, and a
missing row in `expressive-motion.md`, whose What moves table claims to be
complete and does not mention menus at all.

**D-022 gives Task Details a Delete, in the app-bar overflow.** The screen exists
for deciding about one task, and deciding a task is not worth doing is one of the
outcomes; deletion was reachable only from a row's long-press menu, so the screen
entirely about a task was the one place you could not throw it away.

A menu rather than an icon button for three reasons. An unlabelled trash icon is
the least legible form of the most destructive action. An icon in the bar sits
one tap from Back, in the corner a thumb reaches for when leaving, which is the
mistake `expressive-components.md` already refused for the row menu as
"constructive before destructive". And the bar's trailing slot is the overflow on
the three primary screens, so a different control there would make one slot mean
two things.

**The checkbox stays a checkbox**, and that was the other half of the question.
Completion is reversible state, not a one-way action: unticking is how a task is
reopened from the Logbook, and a "Mark done" button would have to become "Mark
not done" on a completed task. Three actions, three weights: Start focus is
full-width because it is the payoff, completion is a checkbox, Delete is a menu
item because it is rare and terminal.

No confirmation dialog, because deletion is a soft delete raising the same undo
offer every list raises and the screen already hosts the snackbar. Deletion also
does not navigate: the task leaving `allTasks` is what pops the screen, so there
is one exit rather than two that could disagree. The board already drew the
overflow, so only the code moved.

**A doc-versus-code divergence turned up while doing it.**
`expressive-components.md` says the row menu's Delete "is labelled in `error`"
and the code had been drawing it in the default colour since it was written. Both
Deletes are error-coloured now.

**Task Details is built, and was reviewed against the design.** It matches:
a full screen on `task-details/{taskId}` with `TaskDetailsSheet.kt` and its host
deleted, no Save, borderless title and notes committing on blur, a bar with a
back arrow and `paneTitle` instead of a drawn title, the overflow omitted, and
Repeat held to `Recurrence`'s four periods per D-019. `DatePresets.kt` improved
on the spec: both presets resolve strictly after today, so neither can silently
do nothing on the day it names. 600 unit tests pass, and the instrumented tests
assert the three regions at 100% and 200%, immediate commit, and both preset
definitions by name.

**Two things were fixed in review, and both were the same mistake.**

`PlanRow` had hand-rolled the segmented treatment with private `16.dp`, `4.dp`
and `2.dp` constants while every other collection in the app reads
`ListItemDefaults.segmentedShapes`, `segmentedColors` and `SegmentedGap` from
Material. It now wraps `SegmentedListItem` like `TaskRow` does, and the three
constants are gone. This is the code arriving at the same drift the board had,
from the other direction: a second copy of three numbers is how one group of
rows ends up disagreeing with itself about its own corners.

`TaskDetailsScreen` had bypassed `FocuslistTopAppBar` for a raw `TopAppBar`,
because the shared component required a non-null title and this screen has none.
The component's own KDoc says it exists because screens that wrote their own bar
drifted apart. `title` is nullable now, and the reason a screen may pass null is
documented on the parameter, so the next title-less screen has a supported route
rather than a precedent for bypassing.

**Reminder health moved onto the shared bar too**, so `FocuslistTopAppBar` is
now the only file in `ui/` that references `TopAppBar` at all. It also gained
the heading semantics the shared bar applies and its own bar did not, which a
screen reader uses to say where the user is.

**Task Details was the work in progress.** D-018 replaced its sheet-and-Save
design with a picker-driven full screen, and the board and the documents are
settled while the code still holds the superseded version. That is the gap being
closed. Phase 4, the Glance widget and real recurrence, comes after.
(Recurrence has since been pulled forward under D-027; the widget has not.)

It is next rather than early: the four items below shipped, so the reason
`task-details.md` gave for deferring it no longer holds. Two boundaries stood at
the time. **Repeat is not rebuilt**, which D-027 has since reversed and the
section above records. **The overflow is omitted** until its contents are
decided, since deletion already lives in the row's long-press menu.

Everything below this line describes work already done.

---

## What shipped before it

**D-021 is implemented: the app colours what it knows.** `CheckState` had three
values and the screen drew two, so a manufacturer feature inferred from
`Build.MANUFACTURER` rendered exactly like a permission the user had refused.
Every OnePlus, OPPO, Realme, Xiaomi, Redmi, POCO, Samsung, Huawei and Honor user
saw a permanent red "Action needed" from first launch.

`ReminderHealthState` gains `WorthChecking`, and `state` is now ordered by
certainty: a recorded miss, then a `Blocked` check, then a `Warning`, then
Ready. `firstFailing` split into `firstBlocked` and `firstWarning`, both keeping
the worst-cost ordering the KDoc argues for. The screen's headline gains its
fourth branch, the check rows tint per state, the three rows became one
segmented group through `ListItemDefaults`, and the badge container is gone: the
glyph sits in a 40dp slot and carries the colour itself.

**The entry said tertiary and the render refused it.** `tertiaryContainer` is
`#FFD7E3` against `errorContainer` at `#FFD8D6`, one step apart in green, so the
caution and the error were indistinguishable. `reminder-health.md` already
recorded that; D-021 did not, and now carries the correction. Warning is
neutral, and the second cue is a question mark rather than a colour.

Verified on the emulator with the manufacturer temporarily forced to OnePlus,
since a Pixel infers no restriction. The Warning-only screen reads calm: no red
anywhere, "Worth checking", and "Sleep standby can delay reminders. Catimo
cannot tell whether it is on." The mixed state renders both at once, a red
Notifications row and a neutral Sleep standby row on the same screen, which is
the rule that certainty belongs to the check rather than to the headline. The
override was reverted.

**Task Details is rebuilt to D-018.** `TaskDetailsSheet.kt` and its host are
gone, about a thousand lines of them, and `TaskDetailsScreen.kt` replaces them:
a `task-details/{id}` route, three regions, five picker rows that commit as they
are chosen, and no Save. It is a room, so back returns to the list the row was
tapped on and the back stack does that without help.

The four lists lost their `openTaskId` state and their sheet host and take an
`onOpenTask` instead. `allTasks` is new on the view model, because a screen
reached by id from any list cannot read the list it came from: a task
rescheduled off Today while its details were open would stop being found and the
screen would close on the edit that had just been made.

**Two things are deliberately absent**, and both are recorded rather than
forgotten. The Repeat row picks one of `Recurrence`'s four periods or none; the
board's nine-frame editor with an interval, a weekday set and an end condition
is Phase 4 per D-019. And the overflow is omitted, since its contents are
undecided and deletion already lives in the row's long-press menu.

`thisWeekend` and `endOfWeek` are pure functions in `core/domain`, because D-018
had to fix their meaning and a fixed meaning is worth asserting: eight tests
cover both, including that neither ever resolves to the day it was pressed on.

**One document was stale and is corrected.** `expressive-components.md`'s Task
Details section still described the two-page sheet, the typed due date, the
absent Reminder row and the disabled-while-invalid confirm, which contradicted
D-018, `task-details.md` and the board at once. Its own row-family section had
already been updated for the Plan row, so the file disagreed with itself.
`CLAUDE.md` resolves it — `docs/decisions.md` wins on scope — and the section is
rewritten rather than left for the next reader to trip over.

**Today and Quick Add are built, and that was a deliberate deviation from the
plan.** This section said the design pass had happened and Phase 4 was
next. Phase 4 is not next. The Glance widget and real recurrence wait, and the
work in progress is the screen the design pass settled: D-011, D-012 and D-013,
taken in that dependency order.

The reason is D-012. Today is the screen `PRODUCT.md` calls the centre of the
app, and it has never answered the question it exists for. The design pass
settled how it should. Building Phase 4 on top of an information architecture
that is about to change is the mistake Phase 3 was sequenced to avoid, and the
Focus now card changes Today's. Phase 4 comes after this.

Four things, in an order that was not arbitrary. **All four are done**, and
what follows describes what shipped rather than what was planned:

1. **D-013, Focus pause and resume.** A prerequisite rather than a
   nice-to-have: the Focus now card's first and strongest reason is "resume
   paused focus", and without a session that can be paused and that survives
   leaving the sheet, that reason cannot fire.
2. **The Focus now card**, with all three of D-012's reasons, since step 1
   supplies the first.
3. **Today's four bands**: Overdue, No time set, Later today, and Completed as
   a collapsed disclosure carrying a count.
4. **Quick Add per D-011**: a trailing time read alongside the trailing day,
   setting a reminder, named by one dismissible chip.

**Today and Quick Add already exist in code**, contrary to how this work was
first described. `TodayScreen.kt` carries the collapsing app bar, the segmented
collection, the FAB and the undo host; `QuickAddSheet.kt` carries the trailing
day, its marking and its supporting line. Steps 3 and 4 are changes to working
screens. What was genuinely absent is the Focus now card and any notion of a
paused session, and git history has never held either.

**One thing the design did not settle, and the code had to.** The extension
behind +5 min does not move the session's origin, although pausing does. Moving
it underflowed: elapsed time floors at zero, so shifting the origin into the
future left the five minutes silently eaten and the estimate alarm rescheduled
to the moment it already pointed at. `extendingPushesTheAnnouncementOutByFiveMinutes`
is the test that caught it, and `FocusSession`'s KDoc carries the reason.

**Verified.** 592 unit tests and 158 instrumented tests pass, the latter on the
Pixel 10 emulator with `ANDROID_SERIAL` pinned. The instrumented run covers
Focus's six states, that leaving pauses rather than stops, Quick Add's chip and
its dismissal, and Today's collapsed Completed band.

**Watched on the emulator rather than tested**, because motion cannot be
asserted: the shape morphing from `Cookie4Sided` to `Cookie12Sided` when a
session starts, the Focus now card arriving and leaving on `reveal`, and the
Completed disclosure opening. All read as intended. The card's paused reason,
the readout counting down, and the FAB clearance at the true end of the list
were confirmed by screenshot.

**Three tests were rewritten rather than repaired**, because they asserted
designs that are now retired. `FocusSessionSemanticsTest` asserted the queue: a
"Next:" footer, completing advancing to the following task, and an empty state
when the queue ran dry. `TodayScreenSemanticsTest`'s undo case asserted a
completed row that is now inside a collapsed disclosure. A view model test
asserted that completing kept the sheet open, which `focus.md` reversed.

**One bug worth naming**, because it is the exact trap `focus.md` warns about.
The sheet ended Focus when `focusedTask` was null, and every exposed flow begins
on a placeholder before storage has answered, so opening Ready closed the sheet
on the way in. The task being genuinely gone is now watched in the view model
against `repository.observeTasks()`, which only emits once it has really read.

**The header is built to D-017 and D-020, and the code no longer trails them.**
`FocuslistTopAppBar` wraps the compact 64dp `TopAppBar`, pinned, with a title
and one side slot and nothing else. The `subtitle` and `scrollBehavior`
parameters are gone from the component, so no screen can pass either.

Four screens changed with it. Today lost the planned-minutes pill, then the
date, then its `exitUntilCollapsedScrollBehavior` and the `nestedScroll` that
fed it. Inbox lost its waiting count. Upcoming and Logbook lost their scroll
behaviour. `todayPlannedMinutes` went with the pill, being its only caller, and
`AppBarTrailingTextAlignment` went with the right-aligned subtitle it was
measured for. Nothing in the app draws `displaySmall` any more.

Verified on the emulator: all four bars are 64dp with no subtitle, and Today's
Completed disclosure now sits on the opening view without scrolling, which is
the 88dp D-020 bought.

**A design review of Focus then ran against the board and produced D-014 and
D-015.** The board and the design documents changed; no Kotlin was touched in
that pass. Chapter 09 of `Focuslist — M3 Expressive` and section 07 of
`Focuslist — Components` both carry it.

**The Focus code was behind the design and has been brought in line.** D-013
was already substantially built, uncommitted, against the shape design D-014
retires: `Circle` to `Clover8Leaf` for a task with an estimate, a six-shape ring
for one without, both driven by a continuous `shapeProgress`. All of it is gone.
`FocusScreen.kt` is rewritten to the current design: title above the shape as
the heading, `Cookie4Sided` and `Cookie12Sided` morphing on a state change only,
the readout at Headline Small inside a 180dp shape, one status line, a round
icon button beside a tonal Complete, and a chevron in place of the close X.

`FocusProgress.kt` and `FocusProgressTest` were deleted rather than adapted,
because D-014 leaves nothing needing a fraction. The three cases worth keeping
from that test — overrun, a clock that has gone backwards, and a missing
estimate — are asserted against `FocusSession` instead. **`focus.md`'s
Verification section still names `FocusProgressTest` and the function**; that is
the one place the documents now trail the code.

D-015 cost a second change worth naming: the sheet's visibility is no longer the
same fact as the session existing. Leaving pauses, so a paused session outlives
the sheet, and a sheet driven by the session would reopen itself on the next
frame. `isFocusSessionActive` became `isFocusSheetOpen`, and the two are written
separately.

D-014 settles what the session shape says. `Cookie4Sided` at rest,
`Cookie12Sided` while running, morphing on a state change only. It no longer
carries progress, because D-013's readout carries that better and the shape was
the worse of the two at it. The task title moved out of the shape to become the
heading, capped at four lines, and the digits dropped to Headline Small inside a
180dp shape. The board had already frozen the old morph in all six states
without recording it, so this writes down what was in front of us.

D-015 settles what leaving does. Close and back both pause rather than stop, so
nothing is discarded in any state. It supersedes D-013's clause that a running
session does not survive leaving, and it removes `focus.md`'s hand-written back
handler.

**The play button is back**, which qualifies the paragraph above. It returns as
a plain 56dp round filled icon button carrying play or pause, not as the origin
of a container transform. The transform stays gone. What brought the button back
is the half of its original argument that never depended on the transform:
holding no text it does not grow with the font scale, and two worded buttons
overflow the 364dp action row at 200%.

**The Logbook's completion-summary card was removed.** It totalled the estimates
of completed tasks and called the result "of finished work", which claims a
measurement the app never makes. `logbook.md` already ruled out "counts,
streaks, statistics, or any summary of throughput", so the board had drifted
from a document that was already right.

**The Logbook panel was then rebuilt from components, and produced D-016.** It
had been the least componentised screen on the board: hand-drawn rows holding a
raw checkbox and two loose text nodes, a hand-drawn header, and day labels at
Title Large, the same size as the screen name. Its rows were 364dp wide at x=24
and 66dp tall against 380 at x=16 and 72 everywhere else, and nothing dimmed a
completed title, so only the checkbox said these tasks were done. It now uses
`Focuslist / Task row` at `Status=Completed` in segmented positions, and the
Section header component the Today bands use.

A `Focuslist / Room header` component was added for this, since the Logbook and
Reminder health both hand-draw a back arrow and a title. Reminder health has not
been moved onto it yet.

D-016 keeps the day grouping, which `logbook.md` had listed as out of scope. The
argument is that the list is ordered by `completedAt` and nothing said so, which
made its one ordering decision invisible. The entry draws the line at a heading
naming a day and nothing more: a count beside it is the step that turns a record
into the scoreboard the original entry was written to prevent.

Empty, loading and read-failure frames were added, which the Logbook had never
had.

**Today's error frame was then rebuilt from the component too, and a copy bug
came out with it.** A sweep found Today's was the only hand-drawn empty or error
block left on the board; Inbox and Upcoming already used `Tone=Error` instances.
Today now matches them, carrying its own calendar icon the way they carry
theirs.

The copy bug is the more useful find. Inbox and Upcoming both read **"Check your
connection and try again."** Catimo has no accounts, no cloud sync and no
backend, all three permanently out of scope in `PRODUCT.md`, so every read is
local and no connection is ever involved. The line sent users to fix something
that was not the problem and implied a server the app does not have. All four
error states now say what did not happen instead, and
`expressive-components.md` carries the rule and the reason.

The copy existed only on the board. It is not in `strings.xml`, which holds no
error strings at all, so nothing shipped with it.

**The two empty-state treatments were then merged into one.** The plain variants
drew a 28dp card on `surfaceContainerLow`; the illustrated ones sat bare on the
background. What decided between them was whether a mascot had been drawn for
that screen yet, which is invisible to a user and made the Logbook's empty state
look unlike every other empty state in the app. The card went rather than
spreading: a card contains related content, and an empty state is the absence of
content. All five states were re-centred after losing 48dp of card padding.

Two icon defects came out of that. Upcoming's error glyph was bound to
`onSurface` rather than `onErrorContainer` and was the filled `schedule` rather
than the outlined one, so it drew a near-black disc on a pink container while
Today and Inbox drew dark red outlines. Both fixed, and
`expressive-components.md` now states the rule.

**`focus.md` and `expressive-motion.md` no longer trail the code.** Both named
`focusProgress` and `FocusProgressTest`, which the Focus rewrite deleted. They
describe `FocusSession` and `FocusSessionTest` now, including the extension not
moving the origin and why.

**A design review of Task setup then ran, and produced D-018 and D-019.** It
found the largest divergence in the project: `task-details.md`, the code and the
board described three different screens. The doc and `TaskDetailsSheet.kt` had a
bottom sheet with typed date fields, a draft and a Save; the board had a full
screen of picker rows committing immediately. The doc also contradicted itself,
naming reminder among the six fields it edits and again under Out of scope
beside recurrence, which `PRODUCT.md` lists as a task property.

D-018 settles it in the board's favour: capture by typing, edit by picking.
`task-details.md` is rewritten to the board. **The code is now deliberately
behind the design**, because rebuilding that screen is not this phase.

The board was refined before the entry was written rather than after:

- The hero lost its tinted card. It was why the title and notes looked
  read-only, and it was showing duration a second time when Duration already has
  a row. Title and notes are borderless fields now, at Headline Small on
  `onSurface` and Body Large on `onSurfaceVariant`.
- The app bar lost its "Task" title, the same correction the Focus header took:
  two centred headings stacked with the upper one naming the app.
- The Plan row went from 4 variants to 12, gaining Pressed and Focused. Settings
  rows already had all three and these open sheets the same way. Its radius went
  28 to 16 and its surface Container High to Low, matching the Task row and the
  Settings row; a 28dp radius on a 56dp row against 16 on a 72dp row had the
  shape scale backwards.
- Row values moved from Primary to `onSurfaceVariant`. Five accented values
  competed with the one button that should carry the accent, and `None` in
  accent colour read as though something were set.
- `Plan` became a Section header instance instead of a 22sp text node as loud as
  the app bar.
- Both date sheets were rebuilt from `Button - tonal` and `Button - outline`
  instances. They had been frames named after the Compose modifiers they wanted.
  **Scheduled had no clear option**, so a task could not be returned to the Inbox
  from this screen; `Next week` gave up its cell, being the one preset
  `date-parsing.md` excludes by name.

**Two defects surfaced on the way.** The Settings row's Pressed state was broken,
rendering white instead of a subtle darkening because its fills were bound in the
wrong order and `On Surface` sat as the base at full opacity; twelve variants
across the navigation and toggle rows are repaired. And 23 duration strings on
the board read `45 min`, `1 hour` and `1 h 30 min` while `DurationLabel.kt`
formats `45m`, `1h` and `1h 30m` and its KDoc says it is shared so nothing "can
never word it differently". All normalised.

D-019 keeps the nine Repeat frames as a Phase 4 design and out of the code.
`Recurrence.kt` excludes an interval, a weekday set, an end date and an
occurrence count in writing, and the board designs all four. Designing ahead is
fine; building ahead is not.

**D-020 took the header back to a compact 64dp bar and removed the date.** The
board had been drawn as an 80dp header with the overflow beside a 36sp title,
which is the middle option `today-screen.md` rejects by name, and the code was
`LargeFlexibleTopAppBar` at 152dp. Correcting the board to the component was the
plan until the subtitle came out.

That is what settled it. The tall bar was never justified by the title:
`today-screen.md` says the rule it overturned was "right about a title" and
overturned it only because the header had gained a payload. D-017 removed the
planned-minutes pill and narrowed the case to the date alone, "thinner than when
the case was made". Removing the date left nothing, so the original rule applies
again and the suspension simply ended.

Today gets 88dp back on its opening view, roughly one and a bit task rows, on
the screen `PRODUCT.md` calls the centre of the app.

**31 frames were reflowed** and `Focuslist / Screen header — Collapsed` is
retired, since a pinned 64dp bar has nothing to collapse to. Four documents
followed: `today-screen.md`'s app-bar and scrolling sections,
`expressive-components.md`'s app-bar section, `expressive-motion.md`'s What
moves table and its collapse note, and `expressive-design-system.md`'s
typography table. Nothing in the app draws `displaySmall` any more.

**The trap is recorded** in `today-screen.md`, because it nearly caught us:
removing the subtitle looks like tidying, and doing it alone leaves 152dp
holding one word, which is worse than either end of the decision. The subtitle
and the height are one decision.

**The four segmented row components now share a base.** Task row, Plan row,
Settings navigation row and Settings toggle row had agreed on radius, surface
and gap only by hand, and had already drifted: the Plan row sat at 28dp on
`surfaceContainerHigh` with no interaction states while the Settings row shipped
three.

The base is variables, not a component. Figma cannot express the obvious version
here, a container the four compose, because that needs a content slot and slots
cannot be created through the plugin API. So three semantic aliases were added
onto the existing scale, `Segmented/Radius outer` to `Corner/Large`,
`Segmented/Radius inner` to `Corner/Extra Small`, and `Segmented/Gap` to
`Spacing/2`. Every corner of every variant binds to them, 240 bindings across 60
variants, and the 24 frames that group rows bind their `itemSpacing` to the gap.
Both sets render identically afterwards, which is the point.

Aliases rather than binding straight to the scale, because binding to
`Corner/Large` would stop anyone typing 28 but would still mean finding all four
again to move the treatment to 20dp.

**Variant structure is still unprotected.** A variable cannot make one component
carry the same states as another, so that half of the drift stays manual.
`expressive-components.md` names the set: Enabled, Pressed, Focused.

**Also answered there:** why these rows are custom rather than kit instances.
The kit does ship a segmented list, as `Type = Segmented (filled)` on its `List`
set rather than a component of its own, which is why searching by name misses
it. It uses the same 2dp gap. What it cannot express is the anatomy: its
`List item` offers Trailing as None, Check Box, Icon, Radio Button or Switch,
and a task row's trailing is a duration string and an icon button together.

**Four design documents were rewritten** against these entries: `focus.md`,
`expressive-motion.md`, `expressive-components.md` and
`expressive-design-system.md`. Two live contradictions surfaced while doing it
and are fixed. `focus.md`'s Completion section still described the session
continuing onto the next task, which needs the queue D-004 removed and which its
own "Which task" section already contradicted. And `expressive-design-system.md`
still said Catimo used seven Material shapes, recording a worry about that
accumulation; it uses two now, and the worry is answered.

---

**A design review of Today ran against the board, and the board changed rather
than the app.** No Kotlin was touched, none of it is reflected in code yet, and
the work is in Figma on the `Focuslist — M3 Expressive` page.

- **Colour.** Every M3 library component on the board was still bound to the
  library's own `Schemes/*` variables, which carry no Catimo modes, so the
  Start focus button and the FAB stayed baseline purple in the wallpaper
  frames. 929 bindings moved onto the `Focuslist Brand` collection. The Light
  value of `On Primary Container` was corrected from `#001356`, a blue that did
  not belong to the indigo primary, to `#403d89`, the tone M3 already uses for
  the Dark mode's primary container.
- **The Focus now card.** Sentence-case label instead of letterspaced caps, the
  estimate moved onto the reason line, and the empty spacer column removed so
  the action sits on the card's own inset. 200dp down to 176dp.
- **The screen header.** Full bleed with its own 16dp padding, matching
  `LargeFlexibleTopAppBar`, and the overflow demoted from a filled primary
  circle to a standard icon button. A separate Collapsed component covers the
  scrolled state, which the board had never drawn.
- **The navigation bar.** M3 colour roles applied. Unselected icons sat on
  `onSurface` while their labels sat on `onSurfaceVariant`, which is what made
  the Upcoming clock the heaviest thing on the bar.
- **Bands.** Task rows in one band now use the `Position=First/Middle/Last`
  variants that already existed, so a band reads as a single connected group.

**The board itself was reorganised.** Scattered sections became one numbered
column with a read-me, a core loop flow map, and a caption on every frame. Dark
coverage was cut from 46 frames to 23, on the rule that dark is kept only where
it proves something light cannot: system surfaces, reminder health contrast,
one frame per surface archetype, widgets, and the theme chooser. Retired frames
live on an `Archive — retired dark frames` page rather than being deleted.

**One thing is blocked and needs a decision before it is built.** The board's
Quick Add sheet shows the parsed day and time as removable chips. That
contradicts two recorded designs. `docs/design/expressive-components.md`
declined chips on Quick Add, because they would be a second way to set a date
alongside the field and would need a precedence rule the user cannot see.
`docs/design/date-parsing.md` puts times of day deliberately outside the
vocabulary and names "tomorrow at 3pm" as the string that must be refused
rather than filed. The board frame already disagreed with both before this
session. Do not build that sheet from the board until it is settled.

---

**Settings was reviewed, and it is the first screen reviewed before it exists.**
Seven board frames, no design document, no route, no screen file. Everything
touched here is in Figma and in docs; no Kotlin was changed, and none of it
should be built yet, because this document puts Settings and backup in Phase 5.

D-024 closes the list at four rows and says what a fifth has to prove. The
review's own value was less in the frames than in that entry: Settings is the
screen a task app accumulates into, and the four candidates refused by name each
already had a home in `date-parsing.md`, in Android itself, in
`today-screen.md`, or in the notification channel.

`docs/design/settings.md` is new and describes all seven frames.

Four defects were fixed on the board:

- **The three section headings were the app bar's type.** 22sp Medium on
  `onSurface` with no text style attached, so "Reminders" was as loud as
  "Settings" over one row, and three headings competed with one title for four
  rows of content. Now `M3/title/small` on `onSurfaceVariant`, matching Today,
  Upcoming and the shared `SectionLabel`. The same correction `Plan` took on
  Task Details.
- **Backup's two headings tied the app bar** at 22sp. Now 16, giving the page
  three sizes: bar 22, heading 16, description 14.
- **All four modal scrims were wrong, in opposite directions.** The three theme
  frames bound the `Scrim` variable and left it at 100%, so the dialog floated
  on black and the screen it belonged to was invisible. The Restore Error frame
  had Material's 30% on a hand-typed colour bound to nothing. All four are now
  `Scrim` at 32%.
- **The Restore Error frame had lost the page behind its dialog**, keeping only
  the illustration. The opaque scrim had been hiding it; fixing the scrim turned
  it into a visibly blank screen. The Backup page is rebuilt behind it.

**One thing was checked and deliberately left alone.** The Backup screen's two
buttons are hand-drawn 364x56 frames rather than kit instances. So are all nine
full-width buttons on the board, across notifications, reminders and settings.
That is a board-wide convention to change in one pass or not at all, not a
Settings defect.

**And one gap the frames cannot show.** Both Appearance rows need persistence
the app does not have: no DataStore, no SharedPreferences, and `FocuslistTheme`
takes `darkTheme` and `dynamicColor` as parameters nothing outside previews
overrides. Phase 5's first bullet is a preferences store, and the screen after
it.

---

**The Duration sheet was reworked to the board, and two of its own comments were
wrong.** This is code, not Figma: `TaskDetailsSheets.kt`. D-026 is the entry.

The builder had followed the board closely across Task Details and five sheets,
and had overridden it correctly four times, three of them closing real gaps the
board left: no way to clear a reminder, no way to clear a duration, and no
marking of the value a date sheet already holds. The Duration sheet is where it
went the other way.

- **Five `Button`s in a `Row` became a `ButtonGroup`.** The row had no answer
  for running out of room: `maxLines = 1`, no overflow behaviour, so it could
  only clip, which is what `expressive-components.md` forbids in as many words.
  `ButtonGroup` moves what does not fit into a menu, and is the connected group
  the board draws. Its `toggleableItem` default supplies the leading, middle and
  trailing shapes, so the connected treatment is Material's rather than a `when`
  on the index.
- **The custom-duration `AlertDialog` became a state of the same sheet.** The
  old code's comment rejected "a second sheet" because stacking windows darkens
  the scrim twice and makes back ambiguous, but a dialog over a
  `ModalBottomSheet` is itself two windows, and the board had never proposed a
  second sheet: its frames are named "SAME ModalBottomSheet" and draw a back
  arrow. Back is one `BackHandler` now, and the Done/Cancel confirm is gone from
  a screen D-018 made commit-as-you-go.
- **The Custom row shows the estimate when no preset holds it.** The sheet could
  not display a custom value at all: 1h 20m lit nothing and said nothing. The
  board solves this with a 48sp readout above the presets, which restates at
  display size a number set one gesture ago; the row carries it instead, as
  `Custom  1h 20m`.

**The overflow menu was then measured, and it never fires.** All five presets
stay laid out inside 380dp from 100% to 200% font scale, so the concern that
sent us measuring, a low-vision user meeting a menu where everyone else sees
buttons, does not happen. Worth recording how that number was nearly got wrong:
the first probe counted labels in the semantics tree and reported five present
at every scale, then reported five present inside 120dp as well, which nothing
that fits can be true of. Overflowed options do not leave the tree, only their
bounds change. The fix was to measure bounds and to squeeze the group into
120dp as a control, so a probe that had stopped working could not pass quietly.

The test that took the measurement was then deleted rather than kept, so the
number is an observation in `docs/decisions.md` and not an invariant. A sixth
preset would move it with nothing to say so.

**Three sheet buttons were 48dp because a floor was standing in for a size.**
`Choose a date`, the date presets and Done all took their height from
`TouchTargetMin`, which is documented as "the smallest an interactive target may
be", not as how tall a button is. 48dp is not on the expressive scale at all,
which runs 32, 40, 56, 96, 136. Start focus had the right 56dp through
`FocusControlSize`, a token named for the Focus screen's action row. Both are
now `FocuslistDimensions.ActionHeight`, and `FocusControlSize` is left
describing only the square clock control it is named for. The board had drawn
every one of these at Medium all along.

**One KDoc described a component that was never built.** `DurationSheet` claimed
"The value shown at the top is the task's current estimate", of a readout that
did not exist. Worth naming as a failure mode: a comment written from the board
rather than from the code beneath it.

Two stale claims in `expressive-components.md` were corrected on the way. Its
Segmented controls section specified `SingleChoiceSegmentedButtonRow` for
recurrence, a control that is nowhere in the app since Repeat became `PlanRow`s;
and its Dialogs section said `DatePickerDialog` was the only dialog while there
were four.

Compiles, and the task unit tests pass.

**The board was then brought to the code**, four frames of it, because the code
is right here and a board that disagrees is the thing the next session builds
from. The 57sp `current-estimate` readout is deleted; the connected group gained
its fifth segment and now reads None / 15m / 30m / 45m / 1h across the full
364dp rather than 295dp centred; Done fills the sheet like `Choose a date` does;
and both date sheets mark the value the task already holds, Today on Scheduled
and No due date on Due, which frame 7 had always done for duration and frames 2
and 4 had never done.

**One state is still undrawn, deliberately.** The Custom row shows an estimate
only when no preset holds it, and the frame's task is set to 45m, so no frame
shows `Custom  1h 20m`. Drawing it would mean a second Duration frame whose
group has nothing selected. The rule is in `task-details.md`; this notes that the
board does not demonstrate it.


---

**The reminder delivery panels were reviewed, and the board mostly held.** 184
paints across the fourteen frames carry no `Schemes/*` leak and no unbound fill,
which is the defect that turned up in the duration segments and the weekday
chips. Three frames are cited by name in the code that draws them.

`docs/design/reminders.md` is new and covers the seven states, the snooze
vocabulary, and the two frames that describe work not done.

- **`notify/Opened from reminder` is retired to the Archive page.** It drew a
  bespoke Task screen with a card around the title and two full-width buttons.
  D-018 removed that card, D-020 removed the "Task" app-bar title and D-022
  settled the screen's actions, so the frame had been contradicting three
  decisions. Tapping a reminder opens Task Details, which section 05 already
  draws; a second drawing of one screen is how the two drift.
- **The snooze sheet drew four chips and now draws three.** Android posts at
  most three notification actions, so `availableSnoozeOptions` takes three of
  the four, and the fourth row was a thing the notification cannot show.
  `This evening` drops out after 18:00 and `Tomorrow 9:00` takes its place,
  which is the rule the frame could not express either way.

**Two frames describe work that does not exist, and the board makes it look
done.** There is no `setFullScreenIntent` anywhere, though D-005 commits to
"full-screen intents where warranted"; and nothing calls `setGroup`, so the
"Catimo · 3 reminders" summary never appears, Android bundling only at four
or more. Both are now recorded as unbuilt rather than left to be discovered.

**What to settle before the full-screen alarm is built:** what warrants one.
Android 14 restricts the permission to alarm and calling apps and the intent
takes over the device, so a reminder is not automatically an alarm. The likely
answer is per-task opt-in, which is a product decision and wants an entry first.
---

**Phase 3: Subtract. Complete**, all four exit criteria met. Phases 1 and 2
are complete. **Phase 4 is next**: the Glance widget and real recurrence
rules, which the plan deliberately sequenced after the information
architecture settled.

**D-009 now rests on three manufacturers, and the OnePlus is the outlier.** A
Galaxy S24 Ultra on Android 16 and a Xiaomi on HyperOS both honour
`setExactAndAllowWhileIdle` exactly, `window=0`, delivering 19ms and 280ms
late. The OnePlus 8T demotes the alarm and delivers about fifty seconds late.
The demotion is a minority behaviour, not how Android works.

The decision stands and is stronger for it. All three reported the permission
granted and all three said "Exact alarms: Allowed", while the behaviour
differed by three orders of magnitude, so only measuring delivery tells them
apart. The Xiaomi also showed the health screen earning its place: Catimo
was absent from MIUI's Background autostart list, so on that phone it could
not have rebuilt its alarms after a restart at all.

**Read the design from `Focuslist — M3 Expressive Clean Slate` (node
`161:3405`) and nothing else.** An earlier page, `Focuslist — M3 Expressive
V1`, is still in the file and disagrees with it. Phase 1 work was started
against the old page twice before that was noticed.

Phase 1 delivered reminders end to end: a time on a task, an exact alarm that
survives a restart and a clock change, an alarm-grade channel, Done and
Snooze on the notification, the snooze arithmetic, a Set Reminder dialog, and
a permission flow covering notifications and exact alarms. All verified on a
OnePlus 8T and on an emulator.

Phase 2 has one delivered slice: **the delivery record**, `schema version 7`
and the `reminder_deliveries` table. `ReminderReceiver` writes a row every
time a reminder fires, carrying the task's title as it read then, the moment
the alarm was aimed at, the moment it arrived, and whether it was announced or
suppressed. Both clocks, as `AGENTS.md` requires. `core/domain/ReminderDelivery.kt`
holds the arithmetic and `LateThreshold`.

It is kept apart from the task because a task's own `reminderDeliveredAt` is
cleared by rescheduling, by completing, and by a recurring occurrence rolling
forward, so the evidence was being destroyed by ordinary use.

Verified on the emulator: a reminder whose alarm fired while the permission
dialog was still on screen recorded `Suppressed`, and granting the permission
produced a second row recording `Announced`.

**One limit to know before building the health screen.** The app cannot detect
that it was demoted. There is no public API to read back a scheduled alarm's
window, and D-009's evidence came from `dumpsys`. So "Exact alarms: Allowed"
will read Allowed on the OnePlus while reminders still drift. The headline
state has to be driven by the delivery record, not by the permission checks.

Second slice: **the health state**, `core/domain/ReminderHealth.kt`. It holds
the three checks and the recent delivery record together, and where they
disagree the record wins. That rule is the point: a screen built on the checks
alone reports Ready on the OnePlus in D-009.

`core/notification/ReminderHealthChecks.kt` answers the two real questions,
and guesses the third from `Build.MANUFACTURER`, because no API exposes these
features and the one that is exposed,
`isIgnoringBatteryOptimizations()`, was measured in D-009 to change an alarm's
flags and leave its window untouched. OnePlus, OPPO and realme map to sleep
standby; Xiaomi, Redmi and POCO to autostart; Samsung to sleeping apps; Huawei
and Honor to protected apps. Every other device is left alone, because a false
warning on a Pixel costs the app the attention the real warning needs.

A restrictive device that then delivers `EvidenceOfHealth` reminders on the
trot stops being warned about, and one late delivery brings the warning back.
The app cannot read the setting, so behaviour is the only evidence there is.

**Only deliveries that were exposed to idle time count as that evidence.**
`schema version 8` records how far ahead each alarm was set, and one set less
than `EvidenceHorizon` ahead proves nothing: the failure needs a phone that has
been left alone, so a reminder set for five minutes' time arriving punctually
says only that `AlarmManager` works. Without this the warning could clear on
daytime reminders and go quiet before the 6am one was missed.

Third slice: **the health screen**, `ui/health/`, with all four states, the
three rows, the settings routing, and the test reminder. Reachable from More
for now; the Clean Slate board puts it in an app-bar overflow, which arrives
with Phase 3's navigation change.

`ReminderHealthState.ActionNeeded` carries which check caused it. The first
build of the screen did not, and blamed the manufacturer's sleep feature for a
notification permission the user had refused. The headline and the button both
read `ReminderHealth.firstFailing`, so they cannot point at different problems.

The test reminder travels the same road as a real one: `AlarmManager`, a
receiver, a notification, and a row in the delivery record. Its thirty-second
futurity is under `EvidenceHorizon` by design, so it can raise a warning and
never clear one. Measured on the emulator at four milliseconds late, against
the roughly fifty seconds D-009 measured on the OnePlus.

Fourth slice: **routing to the manufacturer's own screen**,
`core/notification/DeviceSettingsRoute.kt`. Best effort, and known to be best
effort. Every candidate is resolved against the package manager before it is
launched and the launch itself is guarded, so the user always lands somewhere:
the vendor's screen where the phone permits it, the app's Android settings page
otherwise. The button is named after wherever it will actually arrive.

The packages appear twice, in that file and in `<queries>` in the manifest,
because from Android 11 an undeclared package is invisible and resolves to
nothing. That failure is silent: the deep link would degrade to the generic
page on exactly the phones that need it, and look like the feature was never
built. A unit test checks the two lists agree.

**Two things were learned from the OnePlus 8T, and D-010 records them.** The
activity names every published list gives for this vendor are stale, because
ColorOS and OxygenOS merged at ColorOS 12 and renamed everything to
`com.oplus`. And the replacements are guarded by a signature-level permission,
so they resolve and then throw. There is no ColorOS or OxygenOS entry in the
table as a result, and a test exists to stop one being helpfully added back.

The MIUI, One UI and EMUI entries are still guesses, from the same kind of list
that proved stale here, and none has been tried on that hardware.
`aRestrictedDeviceHasAtLeastOneScreenToOffer` in the instrumented suite is what
will report the next one to go stale. It passes vacuously on a Pixel and means
something on a Xiaomi, a Samsung or a Huawei.

**One caution about that test.** It passed on the OnePlus while the app itself
failed, because it only asks whether a screen resolves, and resolving is not
permission to start it. Resolution and launch are separate questions on these
skins, and only the second one matters. Trust the log line over the test:
`FocuslistReminder` records which screen was opened, or that none was.

**Phase 2's exit criteria are met.**

**Phase 3 and the design pass are the same job.** The Clean Slate board
mentions Anytime and Someday nowhere, and every screen on it carries the same
three-item bar: Today, Inbox, Upcoming, with Logbook, Reminder health and
Settings behind an app-bar overflow. `PRODUCT.md` already described that
information architecture. The code disagreed in two ways. The first is now
fixed. The second was missed, and is described below.

The subtraction went in three commits, in an order that mattered:

1. **Inbox stopped reading placement.** It is now every task that is
   outstanding and undated. This came first because Inbox previously required
   `placement == INBOX`, so an undated Anytime or Someday task appeared in no
   primary list at all. Deleting the screens before this would have left real
   work in the database with nothing able to reach it.
2. **The interface went.** Two routes, two More entries, `PlacementScreen`,
   the triage action on every row, a picker in Task Details, and the undo
   machinery behind the move.
3. **The axis went**, `schema version 9`. `TaskPlacement`, the column, the
   converter, and the `anytimeTasks` and `somedayTasks` helpers.

**`MIGRATION_8_9` is the first migration here that is not an `ADD COLUMN`.**
`minSdk` is 29 and `ALTER TABLE ... DROP COLUMN` needs SQLite 3.35, which
arrives with Android 14, so it would work on a modern test device and break
the upgrade for everyone else. The table is recreated instead: create, copy
naming all thirteen surviving columns on both sides, drop, rename. Never
`SELECT *` in that copy. It passes every test and shuffles people's data.

Verified on the Pixel 10 emulator: 145 instrumented tests including a
version-1 database walked to 9, a version-2 one, and a fully populated
version-8 row crossing the recreated table with only `placement` missing.
524 unit tests.

4. **The navigation bar was reconciled last.** Upcoming moved into the bar,
   the More tab went, and Logbook and Reminder health moved to an app-bar
   overflow. Logbook lost its bottom bar and gained a back arrow, because with
   More gone there was nothing in the bar for it to select and it would have
   drawn three items and told the user they were in none of them.

This last one was nearly missed. Removing Anytime and Someday from More left a
plausible looking menu, and every navigation test asserted the arrangement
that existed rather than the requirement in `PRODUCT.md`, so nothing could
fail. It was found by writing a sentence in `ARCHITECTURE.md` and checking it.
`NavigationSemanticsTest` now asserts the bar holds exactly the three names
`PRODUCT.md` gives.

Two things finished it after the navigation.

**The Focus queue, which had outlived the decision that removed it.** D-004 cut
it; what survived was a resolution rule, `the chosen task while it is still in
the queue, otherwise the head`. That fallback was the queue: completing,
deleting or rescheduling the chosen task moved Focus to whatever headed
today's list. It was visible to the user as a "Next:" footer. The board is
explicit, no Focus frame has a next-task preview and Focus — Ready reads "One
task. Nothing else until you leave Focus."

**The design documents.** Ten under `docs/design/` still described Anytime,
Someday or placement. All seven Superseded banners are gone, which is what
they promised: "this banner comes off in Phase 3, when the document is
rewritten." `navigation.md` and `task-states.md` were rewritten; the rest were
corrected in place. Every remaining mention of those words records that they
were removed rather than describing them as present.

**All three have since been built, and each got its entry first.** This
paragraph named the UP NEXT hero card, Task Details as a full screen and the
Focus timer as not started, and warned that the timer contradicted D-004. True
when written, and not now: the hero card shipped as the Focus now card, D-018
rebuilt Task Details as a picker-driven screen, and D-013 gave Focus pause and
resume by superseding D-004's freeze in as many words. The process this
paragraph asked for is the one that was followed.

The rule it carried outlives its examples and still stands. The board is the
source of truth for how the app should feel; it is not a scope document, and
taking work from it directly is how a phase quietly grows. Do not restyle a
screen the board does not contain.

Update this line when a phase begins and when it ends. It is the first thing
a new coding session should read, and the only place that says where the work
actually is.

Do not build ahead of the current phase. A task belonging to a later phase is
a task for later, even when it is small, even when it is adjacent to what is
being worked on now, and even when it would be quicker to do it while the file
is already open.

---

## Phase 1. Make it tell you

Give the app the feature it is named for and does not have.

Catimo currently schedules exactly one kind of alarm, for a focus session
overrunning its estimate. There are no task reminders at all, the manifest
declares only `POST_NOTIFICATIONS`, and there is no boot receiver, so a device
restart would silently lose every alarm ever set.

Work:

- `reminderAt` on `Task`, with a Room migration and fixtures updated in both
  source sets
- Scheduling through `AlarmManager.setExactAndAllowWhileIdle`
- `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` in the manifest
- A boot receiver that reschedules every outstanding reminder
- An alarm-grade notification channel, separate from the focus channel
- Done and Snooze actions on the notification
- A permission flow that explains why the app is asking
- Setting a reminder from the task details sheet

Exit criteria:

- A reminder set for a time fires at that time with the app closed
- The same reminder fires after a device restart between setting and firing
- The same reminder fires with the screen off and the device idle
- Completing from the notification completes the task, and the app agrees
- Snoozing from the notification reschedules, and survives a restart
- Tests cover scheduling, rescheduling on boot, and the snooze arithmetic

Estimate: 5 to 7 weeks.

---

## Phase 2. Make it trustworthy

The differentiator. Reminders that fire on a Pixel are table stakes.
Reminders that fire on a Xiaomi are the product.

Work:

- Detect manufacturer battery restrictions that
  `isIgnoringBatteryOptimizations()` does not report: Samsung sleeping apps,
  Xiaomi autostart, OnePlus sleep standby, and others
- Route the user to the correct settings screen for their device
- Explain the problem in language a person who does not know what Doze is can
  act on
- Record the time each alarm was scheduled for and the time it actually
  fired, and notice the gap
- A reminder health screen that reports whether the app can currently be
  relied on
- A test reminder the user can fire in 30 seconds to check

Exit criteria:

- On a restricted device, the app says it is restricted before a reminder is
  missed, not after
- Every warning the health screen shows leads somewhere the user can act
- A missed reminder is detected and surfaced rather than passing silently
- The health screen is honest when everything is fine, and does not nag

Estimate: 4 to 5 weeks.

---

## Phase 3. Subtract

Bring the code to the scope `PRODUCT.md` now describes.

Do this before Phase 4, so the widget is not built against an information
architecture that is about to change.

Work:

- Remove Anytime and Someday, and the placements behind them
- Collapse navigation to Inbox, Today, Upcoming, with Focus as a mode and
  Logbook reachable but not primary
- Simplify triage to match
- Remove the Focus queue
- Update the design documents under `docs/design/` that still describe the old
  structure, and remove the superseded banners once they are correct
- Room migration for removed placements, preserving user data

Around 16 source files reference the removed concepts. Check with:

    grep -rlniE "anytime|someday" app/src

Exit criteria:

- No reference to Anytime, Someday or the Focus queue remains in `app/src`
- No document under `docs/` describes a destination the app does not have
- Existing tasks in those placements land somewhere sensible after migration,
  and the migration is tested
- The app builds and all tests pass

Estimate: 2 to 3 weeks.

---

## Phase 4. Reach the home screen

Work:

- A Jetpack Glance widget: today's tasks, tap to complete, tap to add
- Dynamic color on the widget, because that is what makes an Android app look
  native from the home screen
- ~~Recurrence past the current four fixed periods: intervals, weekday sets, and
  an end condition~~ pulled forward under D-027 and done
- Completing a recurring task produces the next occurrence and never makes the
  task vanish

Exit criteria:

- The widget updates when the task list changes, without opening the app
- Completing from the widget completes the task
- The widget is legible in light and dark, and at small and large sizes
- Recurrence rules round-trip through storage and are covered by tests

Estimate: 5 to 6 weeks.

---

## Phase 5. Ship it

Work:

- ~~Settings screen~~ pulled forward under D-028 and done
- ~~Backup and restore to a JSON file the user controls~~ pulled forward under
  D-028 and done
- Full accessibility pass with TalkBack
- Dark theme audit
- Play listing: screenshots, the one-line pitch, privacy policy
- Publish free, with no in-app purchases configured at all

Store listing, one line:

> A calm task app for Android whose reminders actually go off. Free, no
> account, no subscription.

Exit criteria:

- A backup taken on one install restores completely onto a clean install
- Every screen is navigable and comprehensible with TalkBack
- The Play listing makes no claim the app does not deliver
- 1.0 is live

Estimate: 3 to 4 weeks.

---

## After 1.0

In rough evidence order, and only once real users ask:

- The full-screen alarm, per-task opt-in, held here by D-038 rather than cut.
  It has the strongest prior evidence of anything on this list, 202 reviews
  asking for an alarm rather than a notification, and it is still below the
  line for 1.0 because it is a schema change and a lock-screen surface threaded
  through the one subsystem that cannot break. First on this list to be
  reconsidered.
- Subtasks
- Flat Lists, replacing the deferred Projects idea (see `docs/decisions.md`,
  D-003)
- Tablet and foldable layouts
- Wear OS tile

Let the app's own reviews set this order rather than this document.

---

## Totals

Roughly 19 to 25 weeks to 1.0. Longer than it looks, because Phases 1 and 2
are systems work rather than screens.
