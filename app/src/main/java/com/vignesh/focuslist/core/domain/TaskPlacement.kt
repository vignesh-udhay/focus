package com.vignesh.focuslist.core.domain

/**
 * Where a task sits organizationally.
 *
 * These three states are mutually exclusive and describe how far a task has
 * been triaged, not when it should be worked on. Scheduling is a separate,
 * independent axis, so Today and Upcoming are derived from a task's scheduled
 * date rather than represented here.
 */
enum class TaskPlacement {

    /** Captured but not yet triaged. */
    INBOX,

    /** Triaged and actionable. */
    ANYTIME,

    /** Triaged and deliberately deferred. */
    SOMEDAY
}
