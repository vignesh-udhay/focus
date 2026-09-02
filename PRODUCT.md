# Focuslist

## Product

Focuslist is a calm, highly polished personal task manager built specifically
for Android.

It should feel native to Android rather than like an iOS productivity app
ported to Android.

## Core promise

Always know what to do next.

## Core loop

Capture → Decide → Focus → Complete

The ideal experience is:

Open app
→ See today's tasks
→ Choose the next task
→ Start Focus
→ Work
→ Complete
→ Continue to the next task

---

# Product Principles

## 1. Today is the center

The primary question when opening the app is:

"What should I do now?"

Today should make that answer obvious within seconds.

## 2. Capture should be effortless

Creating a task should require almost no decisions.

Users should be able to capture first and organize later.

## 3. Tasks are for action

The task lifecycle is:

Next → Focus → Done

The application should encourage execution rather than task administration.

## 4. Android-native is a product requirement

The application should embrace Android conventions for:

- navigation
- back behavior
- gestures
- system bars
- Material components
- dynamic color
- motion
- notifications
- widgets
- adaptive layouts
- accessibility

Do not reproduce iOS interaction patterns on Android.

## 5. Reduce cognitive load

The application should minimize unnecessary decisions.

Avoid exposing every possible property at once.

## 6. Calm over gamification

The application should not use:

- streaks
- points
- badges
- productivity scores
- unnecessary celebrations
- excessive animations
- motivational noise

The reward is getting the work done.

---

# Information Architecture

Primary destinations:

- Today
- Inbox
- Upcoming
- Anytime
- Someday

Secondary organization:

- Areas
- Projects

Focus is an execution mode, not a separate task-management system.

---

# Core Concepts

## Task

A task represents an actionable piece of work.

A task may have:

- title
- notes
- scheduled date
- due date
- estimated duration
- project
- area
- subtasks
- recurrence
- reminder

## Project

A project is a desired outcome requiring multiple tasks.

Projects should remain simple.

They do not have:

- statuses
- sprints
- owners
- dependencies
- kanban boards

## Area

An area represents an ongoing responsibility.

Examples:

- Work
- Personal
- Health
- Finance

## Focus

Focus is the execution mode for working on one task.

Focus should remove distractions and make the current task obvious.

---

# V1 Features

- Inbox
- Today
- Upcoming
- Anytime
- Someday
- Areas
- Projects
- Tasks
- Subtasks
- Recurring tasks
- Task duration
- Natural-language date parsing
- Focus mode
- Focus queue
- Local-first storage
- Android widgets
- Notifications
- Dynamic color
- Light theme
- Dark theme
- Adaptive layouts

---

# Explicitly Out of Scope

Do not implement these unless explicitly requested:

- habits
- gamification
- social features
- team collaboration
- Kanban
- goals
- second-brain functionality
- full calendar replacement
- AI chat
- complex analytics
- complex priority systems
- plugin systems
- unnecessary customization

Feature requests must be evaluated against the core product promise before implementation.

---

# Navigation

## Compact layouts

Primary navigation:

- Today
- Inbox
- Focus
- More

More contains:

- Upcoming
- Anytime
- Someday
- Areas
- Projects
- Settings

## Expanded layouts

Use an Android-appropriate navigation rail or drawer.

Do not simply stretch the phone layout across a tablet.

Use adaptive layouts appropriate to the available window size.

---

# Design Philosophy

Material 3 is the foundation.

Material provides the system language.

Focuslist provides the product personality.

The UI should feel:

- calm
- responsive
- precise
- native
- modern
- restrained

Avoid:

- excessive cards
- excessive pills
- excessive rounded containers
- unnecessary gradients
- decorative dashboards
- visual clutter

---

# Interaction Philosophy

The most common action should be obvious.

Common operations should require minimal interaction.

Examples:

Complete a task:
- tap checkbox
- or use an equivalent accessible action

Create a task:
- open Quick Add
- type
- submit

Start Focus:
- choose task
- tap Focus

Finish:
- complete task
- show next task

Avoid confirmation dialogs for low-risk reversible actions.

Use undo where appropriate.

---

# Android Requirements

The application must support:

- edge-to-edge layouts
- system insets
- predictive back where applicable
- dynamic color
- light and dark themes
- system font scaling
- accessibility services
- compact and expanded window sizes
- widgets
- notifications
- app shortcuts

The UI must not depend exclusively on gestures.

---

# Performance

The application should feel immediate.

Local user interactions must not wait for network operations.

The eventual architecture should be local-first:

User action
→ local state update
→ immediate UI update
→ background synchronization

Network availability must not determine whether basic task management works.

---

# Agent Development Rules

Before implementing a feature:

1. Read this file.
2. Read AGENTS.md.
3. Inspect existing code.
4. Reuse existing components.
5. Follow the established design system.
6. Make the smallest coherent change.
7. Build the project.
8. Test the affected behavior.

Do not invent product behavior.

Do not add features that were not requested.

Do not introduce dependencies without a clear reason.

Do not rewrite working code unnecessarily.

---

# Definition of Quality

A feature is not complete merely because it compiles.

It should:

- behave correctly
- feel native to Android
- follow Material 3
- support light and dark themes
- respect accessibility
- work across relevant screen sizes
- have appropriate loading, empty and error states
- use appropriate motion
- avoid unnecessary visual complexity

The goal is not maximum functionality.

The goal is an exceptionally good task-management experience.