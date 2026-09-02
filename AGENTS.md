# AGENTS.md

## Before making changes

Read `PRODUCT.md` before implementing or modifying product functionality.

Understand the existing code before creating new abstractions.

Do not invent product behavior, features, navigation, or UI patterns.

---

# Technology

This is a native Android application.

Use:

- Kotlin
- Jetpack Compose
- Material 3
- Android platform APIs where appropriate

Prefer official Android and Jetpack libraries.

Do not introduce a dependency when the Android SDK or existing project
dependencies already provide the required capability.

---

# Architecture

Keep responsibilities separated.

UI:
- Composable functions
- UI state rendering
- user interaction callbacks

Presentation:
- ViewModels
- screen state
- UI-related business orchestration

Domain:
- business rules
- use cases when they provide meaningful separation

Data:
- repositories
- local persistence
- synchronization

Do not put business logic inside composables.

Do not create abstractions merely for the sake of abstraction.

Prefer simple, understandable code.

---

# Design System

Material 3 is the foundation of the visual system.

Use the existing Focuslist theme and design tokens.

Before creating a new component:

1. Check whether an existing Material 3 component can be used.
2. Check whether an existing Focuslist component can be reused.
3. Only create a new component when there is a real design or behavioral need.

Do not hard-code colors throughout the UI.

Use Material color roles such as:

- primary
- onPrimary
- surface
- surfaceContainer
- onSurface
- onSurfaceVariant
- outline

Use the Focuslist typography definitions.

Use the Focuslist spacing definitions.

Use the Focuslist shape definitions.

Do not introduce arbitrary spacing values when an existing spacing token
is appropriate.

---

# Android-native UX

The application should feel native to Android.

Follow Android conventions for:

- navigation
- back behavior
- system bars
- edge-to-edge layouts
- gestures
- touch targets
- Material components
- dynamic color
- notifications
- widgets
- accessibility
- adaptive layouts

Do not copy iOS interaction patterns.

Do not recreate an iOS-style navigation hierarchy simply because another
product uses it.

---

# UI implementation

Every screen should consider:

- normal state
- empty state
- loading state when applicable
- error state when applicable
- dark theme
- light theme
- accessibility
- font scaling
- compact width
- expanded width

Do not design only for one fixed phone size.

Avoid unnecessary:

- cards
- borders
- shadows
- gradients
- decorative containers
- pills
- animations

Visual hierarchy should come primarily from:

- typography
- spacing
- color roles
- alignment
- component hierarchy

---

# Motion

Motion should communicate a meaningful state change.

Good uses include:

- task completion
- expanding/collapsing content
- navigation transitions
- showing or dismissing temporary UI
- focus mode transitions

Do not add animation merely because animation is possible.

Animations should be:

- purposeful
- responsive
- interruptible where appropriate
- respectful of reduced-motion/accessibility settings

---

# Interaction

Prefer direct manipulation.

For common actions:

- minimize unnecessary confirmation dialogs
- provide undo for reversible destructive actions where appropriate
- maintain predictable touch behavior
- provide accessible alternatives to gesture-only interactions

Task completion should feel immediate.

Local interactions should not wait for network operations.

---

# Product scope

`PRODUCT.md` defines the current product scope.

Do not implement features merely because they seem useful.

Do not add:

- AI features
- social features
- gamification
- analytics dashboards
- collaboration
- unnecessary customization
- backend infrastructure

unless explicitly requested.

---

# Dependencies

Before adding a dependency:

1. Check whether an existing dependency provides the capability.
2. Check whether the Android SDK provides the capability.
3. Consider maintenance and APK size.
4. Explain why the dependency is necessary.

Do not add libraries simply because they are popular.

---

# Code quality

Prefer:

- readable code
- small composables
- meaningful names
- immutable UI state
- unidirectional data flow
- Kotlin idioms
- minimal duplication

Avoid:

- giant composables
- deeply nested conditional logic
- duplicated UI implementations
- premature abstractions
- unexplained magic numbers
- dead code

---

# Testing

When changing behavior:

- build the project
- run relevant tests
- verify the affected UI manually when appropriate

Do not consider a feature complete merely because it compiles.

For important user interactions, add appropriate tests.

---

# Agent workflow

For every requested feature:

1. Read `PRODUCT.md`.
2. Read this file.
3. Inspect the existing implementation.
4. Identify reusable components and design tokens.
5. Implement the smallest coherent change.
6. Build the project.
7. Run relevant tests.
8. Review the implementation against the product and design requirements.
9. Report what changed and any remaining issues.

Do not modify unrelated code.

Do not refactor unrelated areas while implementing a feature.

If a requirement is ambiguous, identify the ambiguity rather than inventing
a major product decision.

---

# Definition of done

A feature is complete when:

- it implements the requested behavior
- it follows PRODUCT.md
- it follows the existing design system
- it builds successfully
- relevant tests pass
- it works with light and dark themes
- accessibility has been considered
- adaptive layouts have been considered
- the implementation does not introduce unnecessary complexity