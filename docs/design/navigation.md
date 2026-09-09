# Navigation

How the app's screens are arranged, and how a user moves between them. Any one
screen's own design lives in that screen's document.

---

# Primary navigation

The bar's visual treatment is specified in `expressive-components.md`; the
adaptive direction for wider windows is in `expressive-design-system.md`.

A Material 3 `NavigationBar`, on the three primary destinations:

- Today
- Inbox
- Upcoming

The three `PRODUCT.md` names, and the three the Clean Slate board draws on
every screen that has a bar at all.

**Focus is not among them, and neither is More.** Both used to be.

Focus was a destination whose content was one task rather than a list. It
became a sheet opened from the task it is for, because a bar entry landed the
user on whichever task happened to head a queue with nothing to say why that
one. `focus.md` records the reversal in full, and `docs/decisions.md` D-004 is
the decision that removed the queue underneath it.

More was a bar item standing in for a screen `PRODUCT.md` never defined. What
sat behind it are not places among the lists, so they moved to the app-bar
overflow described below.

The bar is on the three primary destinations rather than everywhere, which is
a change. It used to be on every screen so that no list was a dead end. With
three items and nothing else in the bar, a screen that is not one of the three
has nothing to select, and a bar showing three items with none of them current
tells the user they are nowhere. Those screens take a back arrow instead.

One exception: a running Focus session hides the bar, and hides the rail with
it. That is a mode the user started and can stop, not a destination they
wandered into, and the session carries its own visible exit. `focus.md` covers
why the distinction is what makes it safe.

[IMPL] Decided above the graph, in `FocuslistNavHost`, rather than inside the
screen. The bar is handed to screens and could have been withheld by one, but
the rail is a sibling of the whole graph, so the two would behave differently
if the decision were made in either place other than here.

## The selected icon is filled, and the board disagrees on purpose

Material's convention is a filled icon for the selected destination and an
outlined one for the rest, with the indicator pill and the label weight
carrying the rest of the signal. That is what the app does.

**The Clean Slate board shows all three outlined, and that is not a mistake to
copy.** The Material 3 Figma kit the board draws from has 141 icons and no
filled calendar, under `today`, `calendar`, `event` or `date`. Today is the
default destination, so it is the one icon that could not be filled, and two
filled out of three reads as a defect rather than a system. Outlined-consistent
was the correct choice for the board given what it could reach.

`res/drawable` is not limited that way. It already holds `ic_today_filled` and
`ic_inbox_filled`, so the app can do what Material asks.

This is therefore a deliberate divergence: the app is filled-when-selected, the
board is outlined, and the board is the one that is constrained. Do not
"correct" the code to match a frame. If a filled calendar ever reaches the kit,
update the board and delete this section.

`ic_upcoming_filled` has since been drawn and is in `res/drawable`, so the bar
can be consistent. This paragraph used to say it was the one asset the app still
needed.

---

# The app-bar overflow

Three dots at the end of the header row, on Today, Inbox and Upcoming, and
nowhere else. It carries destinations, and that is what makes it navigation
rather than a screen's own action menu. A room may still grow a menu of its own
actions under the same glyph without contradicting this; Task Details is the
screen that will, and its contents are undecided, so it currently has none.

It opens, as labels with no icons:

- Logbook
- Settings

Reminder health is the first row inside Settings rather than a second route in
this menu. D-029 records why the duplicate was removed. `settings.md` describes
the seven board frames and D-024 closes the list at four rows.

Not on the screens it opens, because each of those already has a back arrow
and offering a way in from inside would be a loop.

The menu contents are one list in `FocuslistNavigation.kt`, shared by whatever
anchors it, so the bar and the rail cannot drift into offering different
places.

---

# Rooms, not places

Logbook and Settings are reached from the overflow, draw a back arrow, and show
no navigation bar. Reminder health is the room behind Settings' first row and
follows the same back-stack rule.

This is the rule the top app bar enforces: a screen wears either the bar and
an overflow, or a back arrow and no bar. Never both. A back arrow and a bottom
bar are two answers to the same question.

---

# The graph

`FocuslistNavHost` declares one flat graph with Today as the start
destination. Every screen is a direct child of it; there are no nested graphs,
because nothing yet needs one.

Routes are names rather than positions:

    today  inbox  upcoming  logbook  reminder-health  task-details/{id}

**Task Details gained a route with D-018.** It was a `ModalBottomSheet` opened
over whichever list the row was tapped on, and it is now a full screen, which
makes it a destination rather than state. It is a room by the rule above: a back
arrow, no navigation bar, and back returns to the list it was opened from.

Focus has no route. It is a `ModalBottomSheet` over whichever screen asked for
it, so it is state rather than a destination, and dismissing it pauses the
session per D-015.

The `anytime` and `someday` routes are gone. They were two routes over one
screen, the same query with one constant changed. `docs/decisions.md` D-002
removed the concept and schema version 9 removed the column behind it.

---

# Back

Back is the navigation back stack's own behavior, not a hand-written one.

- from Logbook or Settings, back returns to the list it was opened from
- from Reminder health opened through Settings, back returns to Settings
- from Inbox or Upcoming, back returns to Today
- from Today, back leaves the app

The single hand-written exception is a running Focus session, which takes back
first: it dismisses the sheet and stops the session. Without it, back would
leave a session running with nothing on screen pointing at it.

Selecting a bar destination clears everything above Today first, so tapping
around the bar never grows the stack. This is deliberately done without
`saveState` and `restoreState`: those save the popped stack and put it back on
return, which suits a bar whose items each own a nested graph. This graph is
flat, with the overflow destinations sitting on top of it, so restoring would
return the user to a screen they had already left.

---

# The shared view model

Every destination reads one `TaskListViewModel`, so the lists agree with each
other and a single undo offer stands for the whole app rather than per screen.
An action taken on one screen is still undoable after moving to another.

It is built once in `FocuslistNavHost`, above the graph, and handed to each
screen as a parameter. Where it is built decides how many exist: `viewModel()`
resolves against the current `LocalViewModelStoreOwner`, and inside a
destination that owner is the destination's own back stack entry. Building it
there gives every list a view model of its own and splits the undo offer
between them. The lookup is private to the navigation layer so that a screen
cannot do this by accident.

Reminder health is the exception. It reads a different table and asks a
different question, so it has its own view model built from the application's
repositories.

---

# Out of scope

Not part of navigation yet:

- a navigation drawer, or adaptive navigation beyond the existing rail
- deep links, nested graphs, and custom transitions
- predictive back tuning beyond the framework default

---

# Verification

`NavigationSemanticsTest` covers the chrome at 100% and 200% font scale: every
destination is labelled, the current one publishes its selected state, the bar
holds **exactly** the three destinations `PRODUCT.md` names, nothing is
selected on an overflow route, and the overflow opens the destinations it
names.

That "exactly" is the half the suite used to be missing. It asserted whatever
the bar happened to hold, so when Upcoming sat behind More in contradiction to
`PRODUCT.md`, nothing failed and the gap survived three commits. A test that
only describes the code cannot notice the code is wrong.

The back stack itself is still verified by hand. The architecture was
deliberately not weakened to make it unit-testable, and that has not changed.
The rail is out of scope for those tests, which are phone-layout only.
