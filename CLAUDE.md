# Focuslist, Claude Instructions

## Start of every session

Read these, in this order, before touching anything:

1. `ROADMAP.md` -> the "Current phase" section. This says what work is in
   scope right now.
2. `PRODUCT.md` -> product requirements and design philosophy.
3. `docs/decisions.md` -> why the product is shaped this way, and what was
   deliberately cut.
4. `ARCHITECTURE.md` -> how the app is built, and where new code goes.
5. `AGENTS.md` -> engineering and implementation rules.

These files are the source of truth for the project. Where they disagree with
each other, `docs/decisions.md` wins on questions of scope and
`AGENTS.md` wins on questions of implementation.

## Core rule

Do not invent product behavior.

If a request conflicts with `PRODUCT.md`, identify the conflict before
implementing it.

## Drift guard

This project is built across many short sessions. The main risk is not bad
code, it is the app quietly becoming a different app, one helpful addition at
a time.

Three specific things to refuse by default:

**Do not rebuild what was cut.** `PRODUCT.md` has a "Not in this product"
section, and every entry in it points at a decision in `docs/decisions.md`.
Anytime, Someday, Areas, Projects and the Focus queue were removed on
evidence. They will look useful again. Read the decision before acting.

**Do not build ahead of the current phase.** A task from a later phase is a
task for later, even when it is small, adjacent, or would be quick while the
file is already open.

**Do not reverse a decision silently.** To change scope, add a superseding
entry to `docs/decisions.md` first, saying what new information changed the
answer. If the reason cannot be written down, do not make the change.

When a request would do any of these three, say so before implementing, and
say which decision it touches.

## What the app is for

Focuslist tells you about your work at the moment it matters, and it does not
miss.

A reminder that does not fire is the most severe class of bug in this
product. Higher severity than a crash: a crash is visible, a missed reminder
is not.

Design is the finish, not the pitch. Keep the calm Material 3 Expressive
direction, and do not sequence design work ahead of reliability work.

## Development workflow

Before implementing a feature:

1. Read the relevant product requirements.
2. Check the feature belongs to the current phase.
3. Inspect the existing implementation.
4. Reuse existing components and design tokens.
5. Make the smallest coherent change.
6. Build the project.
7. Run relevant tests.
8. Review the result against the product and design requirements.

## Design

Focuslist is a native Android application.

Use the existing Material 3 theme and Focuslist design system.

Prefer Material 3 components over custom implementations.

Do not introduce arbitrary colors, spacing, shapes, typography, or motion
when an existing design-system value is appropriate.

Some documents under `docs/design/` still describe the pre-Phase-3
information architecture and carry a "Superseded" banner at the top. Where a
banner and `PRODUCT.md` disagree, `PRODUCT.md` is right.

## Code

Prefer simple, readable Kotlin and Jetpack Compose.

Do not introduce unnecessary dependencies.

Do not refactor unrelated code while implementing a feature.

Do not consider a feature complete merely because it compiles.

## When requirements are unclear

Do not make major product decisions silently.

State the ambiguity and propose the smallest reasonable interpretation.

## End of a session

If the work changed where the project stands, update the "Current phase"
section of `ROADMAP.md`. A session that leaves that line stale costs the next
session the time it takes to work out what happened.
