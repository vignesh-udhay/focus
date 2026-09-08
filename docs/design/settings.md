# Settings

Four rows on one screen, plus two places they lead: the theme chooser and
Backup & restore.

**Built under D-028.** `SettingsScreen.kt` is reached from the app-bar overflow,
and Backup & restore is the room behind its Data row. The work was pulled ahead
of the remaining Phase 4 widget without moving the rest of Phase 5. D-024 still
carries the argument for what the closed four-row screen holds.

The one settings-shaped file that does exist,
`core/notification/DeviceSettingsRoute.kt`, opens *Android's* settings for the
reminder-health deep links. It is not this screen and does not become it.

---

# Design system

Row anatomy, the segmented treatment and motion are specified in
`expressive-components.md`. The modal scrim is stated here, because that
document covers the bottom sheet's scrim and not the dialog's. This document
otherwise covers what this particular screen holds and why the list is closed.

---

# What it holds

Three sections, four rows, and that is the whole screen.

    Reminders     Reminder health      Check exact reminder reliability   >
    Appearance    Dynamic color        Use colors from Android         [on]
                  Theme                System default                     >
    Data          Backup & restore     Export or restore a local file     >

Every row carries supporting text, because a settings row whose label alone
explains it does not need a settings screen, and one that needs a paragraph is a
feature wearing a row.

**Reminder health has one permanent entry, here.** D-029 removes its duplicate
from the overflow: two identically labelled routes to the same room asked the
user to choose without changing the result. `PRODUCT.md` principle 1 still
makes reminder delivery the thing the app is for, so contextual warnings and
reminder deep links may open the health screen directly when action is needed.

---

# The list is closed

D-024 is the entry. Settings is the screen every task app accumulates into, and
the four rows are load-bearing rather than a starting point.

Not on this screen, and each already answered elsewhere:

- **A default reminder time.** `date-parsing.md` keeps times of day out of the
  vocabulary; a global default would put them back through a side door.
- **A week-start day, a date format, a time format.** Android already holds all
  three, and `PRODUCT.md` principle 5 says to embrace the platform rather than
  re-ask.
- **Sort order, grouping, density.** `today-screen.md` fixes the bands and
  `task-row.md` fixes the row. A setting here is the app declining to decide.
- **Anything about Anytime, Someday, Areas or Projects.** Removed on evidence,
  D-002 and D-003.
- **Notification sound, vibration, channel importance.** These belong to the
  Android channel, which the system settings own. Duplicating them creates two
  switches that disagree.

The general rule: a setting is a decision the product refused to make. Four is
the number of decisions genuinely belonging to the user, and each is about
either their device or their data rather than about how the app works.

---

# Section labels are labels

`M3/title/small` at 14sp on `onSurfaceVariant`, the same treatment Today and
Upcoming give their band and day headings, and the same the code's shared
`SectionLabel` produces.

The board drew all three at 22sp Medium on `onSurface`, which is the app bar's
own type. "Reminders" was as loud as "Settings", three headings competed with
one title for four rows of content, and none of the three carried a text style
at all. This is the same correction `Plan` took on Task Details, recorded in
`ROADMAP.md` as becoming "a Section header instance instead of a 22sp text node
as loud as the app bar".

---

# The theme chooser is a dialog

A title, three radio rows, no buttons. Selecting a theme applies it and closes
the dialog; tapping outside leaves the current one.

**No Cancel and no OK**, which is the same commit-as-you-go rule D-018 gave the
Task Details pickers. A confirm step on a three-way choice that is instantly
visible and instantly reversible only adds a tap. What stands in for undo is
that the result is the screen you are looking at.

**A dialog rather than a page or a sheet.** Three mutually exclusive options
with no explanation needed is what a Material alert dialog is for, and a
destination for three radio buttons would be a fourth room. The Backup & restore
row opens a page because it has two operations, two descriptions and a file
picker behind it; Theme has none of that.

The three frames are the three selections. System default and Light are drawn in
the app's Light scheme; the Dark frame is pinned to the `Dark` mode of
`Focuslist Brand`, so it shows the consequence of the choice and not only the
radio that was pressed. They exist so the built screen cannot ship with the
selected state guessed.

System default is drawn light, which is one of the two things it can be. That is
a representative rather than a claim, and the dark half of it is the Dark frame.

## The scrim

`Scrim` at 32%, which is Material's own modal value and what
`androidx.compose.material3` applies.

All four modal frames had it wrong, in opposite directions. The three theme
frames bound the `Scrim` variable and left it at 100%, so the Settings screen
behind the dialog was invisible and the frame showed a dialog floating on black.
The Restore Error frame used the right 30% on a hand-typed colour bound to
nothing. Both now use the variable at 32%.

---

# Backup & restore

A page, reached from the Data row.

An illustration reading "Your data stays on this device", then two blocks: a
heading, a line of description, and a full-width button.

    Backup     Export tasks, reminders, recurrence, and settings.  [Export backup]
    Restore    Restore from a Focuslist backup file.               [Restore from file]

**Export is filled and Restore is tonal.** Export is safe and repeatable;
restore overwrites what is on the device. The weaker button is the one that
destroys, which is the same ordering `task-details.md` applies to Delete.

Headings are `M3/title/medium` at 16sp. They were 22sp, tying the app bar, which
put three things at one size on a screen with two ideas on it. The bar is 22, a
heading is 16, a description is 14.

**The illustration is a claim about storage, not decoration.** `PRODUCT.md`
principle 8 and D-001 make local-only a product decision rather than a temporary
state, and this is the one screen where a user asks where their data went. It
says the thing the screen exists to prove.

## Restore errors

A Material alert dialog over the Backup page: "Couldn't restore this file", "The
file may not be a Focuslist backup, or it may be damaged.", with Cancel and
Choose another file.

**The primary action is Choose another file, not Retry.** Retrying the same
damaged file produces the same error, and a button that cannot work is worse
than no button.

**The wording does not guess which of the two it is.** The app can tell a
malformed file from a foreign one, but the user's next move is the same either
way, so naming the cause spends a sentence to change nothing. This is the
certainty rule `reminder-health.md` states for the health screen, applied to a
smaller case.

The frame behind this dialog had lost the entire Backup page and showed only the
illustration, which was invisible while the scrim was opaque and became a blank
screen the moment it was fixed. The page is rebuilt behind it.

---

# Appearance persistence

`FocuslistPreferences` stores the two scalar choices with Android
`SharedPreferences` and exposes them as a `StateFlow`. `MainActivity` resolves
System, Light or Dark before calling `FocuslistTheme`; the dynamic-colour flag is
passed alongside it. Selection therefore changes the visible app immediately
and survives process death, without adding a persistence dependency for two
values the Android SDK already knows how to store.

---

# Dynamic colour is drawn as a control, not as a palette

`Settings — Dynamic color off` differs from the clean slate by one switch. Both
are painted in the `Light` mode of `Focuslist Brand`, which is the app's own
scheme, so neither frame shows what turning the setting off does to the colours.

That is deliberate and worth stating so it is not repeatedly rediscovered:
section 12 owns control state and section 18, `Theming — Dynamic colour`, owns
the palettes, in `Wallpaper warm` and `Wallpaper cool` across Today, Focus and
the notification. Duplicating the palette story into the Settings frames would
mean maintaining it twice.

**The Theme frames do not follow that rule, and the asymmetry is on purpose.**
Dark is pinned to the Dark mode because there are two outcomes and both are the
app's own scheme, so showing one costs one frame. Dynamic colour has as many
outcomes as there are wallpapers, so showing it properly costs a section, and
section 18 is that section.

---

# Out of scope

Not part of this screen:

- an About or version row, until there is something a user needs from it
- a licences screen, until a dependency requires one
- export to anything but the app's own file
- automatic or scheduled backup, which needs a destination the app has decided
  not to have

---

# Implementation status

**Built.** The overflow route, four-row screen, theme chooser, preference store,
Backup & restore page, Storage Access Framework launchers, versioned JSON codec,
and restore-error dialog are in place. Restore parses and validates the whole
file before replacing tasks; device-specific reminder delivery history is
cleared rather than moved to a different phone.

The debug APK and JVM suite build cleanly. Focused emulator tests cover every
task field and both settings through the codec, foreign/future file rejection,
database replacement and delivery-history clearing, the Settings toggle and
theme dialog, and the existing navigation behavior. The Settings and Backup
pages were also inspected on the emulator against board node `161:3409`.

The two row components exist and are current: `Focuslist / Settings /
Navigation row` and `Focuslist / Settings / Toggle row`, both carrying Enabled,
Pressed and Focused, both bound to the shared `Segmented/*` variables. Their
Pressed state was repaired during the Task setup pass, twelve variants that
rendered white because `On Surface` sat as the base fill at full opacity.
