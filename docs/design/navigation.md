# Navigation

> **Superseded in part.** This document still describes the pre-Phase-3
> information architecture, which included Anytime, Someday, or the Focus
> queue. Those were removed on evidence: see `docs/decisions.md`, D-002 and
> D-004. Where this document and `PRODUCT.md` disagree, `PRODUCT.md` is
> right. This banner comes off in Phase 3, when the document is rewritten.

How the app's screens are arranged, and how a user moves between them. Any one
screen's own design lives in that screen's document.

---

# Primary navigation

The bar's visual treatment is specified in `expressive-components.md`; the
adaptive direction for wider windows is in `expressive-design-system.md`.

A Material 3 `NavigationBar`, on every screen:

- Today
- Inbox
- Focus
- More

The order `PRODUCT.md` gives for compact primary navigation.

Focus is a destination like the first two, but its content is one task rather
than a list. The bar does not have to know that; `focus.md` describes what the
screen is.

The bar is on every screen rather than only the primary ones, so no list is a
dead end. Reaching Upcoming from Inbox does not mean going back to Today first.

One exception: a running Focus session hides it, and hides the rail with it.
That is a mode the user started and can stop, not a destination they wandered
into, and the session carries its own visible exit. `focus.md` covers why the
distinction is what makes it safe.

[IMPL] Decided above the graph, in `FocuslistNavHost`, rather than inside the
screen. The bar is handed to screens and could have been withheld by one, but
the rail is a sibling of the whole graph, so the two would behave differently
if the decision were made in either place other than here.

---

# More

More is not a destination. `PRODUCT.md` names it in the bar but defines no
screen for it, so it opens a menu:

- Upcoming
- Anytime
- Someday
- Logbook

`PRODUCT.md` also places Areas, Projects, and Settings behind More. They join
the menu when they exist.

More shows as the current item while the user is on one of its destinations,
because that is where they are. It is a menu rather than a screen for as long
as `PRODUCT.md` declines to define one; four entries sit comfortably in a
menu, and the question of whether it should become a screen is worth asking
again once Areas, Projects, and Settings are real.

---

# The graph

`FocuslistNavHost` declares one flat graph with Today as the start
destination. Every screen is a direct child of it; there are no nested graphs,
because nothing yet needs one.

Routes are names rather than positions:

    today  inbox  focus  upcoming  anytime  someday  logbook

Anytime and Someday are two routes over one screen. They are the same query
with one constant changed, and the route decides which; nothing on screen
switches between them.

They used to share a tab row, on the reasoning that they are the pair a user
flips between. They are not. Anytime is the undated backlog you pick work from
and Someday is a list reviewed occasionally, so they are visited at completely
different rates, which is the opposite of what tabs are for. The row also
duplicated navigation that already existed, since More lists both as separate
entries, and it renamed the screen when the tab changed. A tab that renames the
page is a second destination wearing one.

---

# Back

Back is the navigation back stack's own behavior, not a hand-written one.

- from a secondary destination, back returns to the list it was opened from
- from Inbox or Focus, back returns to Today
- from Today, back leaves the app

The single hand-written exception is a running Focus session, which takes back
first: it leaves the session and stays on Focus, and a second back then leaves
the screen as above. Without it, back would exit a screen whose navigation is
hidden, skipping past the state the user actually wanted out of.

Selecting a bar destination clears everything above Today first, so tapping
around the bar never grows the stack. This is deliberately done without
`saveState` and `restoreState`: those save the popped stack and put it back on
return, which suits a bar whose items each own a nested graph. This graph is
flat, with the secondary destinations sitting on top of it, so restoring would
return the user to a secondary list they had already left.

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

---

# Out of scope

Not part of navigation yet:

- Areas, Projects, and Settings
- a navigation rail, drawer, or any adaptive navigation
- deep links, nested graphs, and custom transitions
- predictive back tuning beyond the framework default

---

# Verification

`NavigationSemanticsTest` covers the bar: every destination is labelled, the
current one publishes its selected state, More reports itself as current on a
secondary route, and the menu opens the four destinations it names. At 100% and
200% font scale.

The back stack itself is still verified by hand. The architecture was
deliberately not weakened to make it unit-testable, and that has not changed.
The rail is out of scope for those tests, which are phone-layout only.
