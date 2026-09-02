# Focuslist — Claude Instructions

Before making changes, read:

1. `PRODUCT.md` — product requirements and design philosophy
2. `AGENTS.md` — engineering and implementation rules

These files are the source of truth for the project.

## Core rule

Do not invent product behavior.

If a request conflicts with `PRODUCT.md`, identify the conflict before
implementing it.

## Development workflow

Before implementing a feature:

1. Read the relevant product requirements.
2. Inspect the existing implementation.
3. Reuse existing components and design tokens.
4. Make the smallest coherent change.
5. Build the project.
6. Run relevant tests.
7. Review the result against the product and design requirements.

## Design

Focuslist is a native Android application.

Use the existing Material 3 theme and Focuslist design system.

Prefer Material 3 components over custom implementations.

Do not introduce arbitrary colors, spacing, shapes, typography, or motion
when an existing design-system value is appropriate.

## Code

Prefer simple, readable Kotlin and Jetpack Compose.

Do not introduce unnecessary dependencies.

Do not refactor unrelated code while implementing a feature.

Do not consider a feature complete merely because it compiles.

## When requirements are unclear

Do not make major product decisions silently.

State the ambiguity and propose the smallest reasonable interpretation.