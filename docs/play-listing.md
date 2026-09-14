# Play Store listing

Phase 5 work. Every claim here is checked against the build rather than
against `PRODUCT.md`, because the exit criterion is "the Play listing makes no
claim the app does not deliver" and the two documents are not the same thing.
Where a line rests on something specific in the code, the evidence is named.

Placeholders to fill before submission are marked `TODO`.

---

## Store listing fields

### App name

    Catimo: Tasks & Reminders

25 of 30 characters. The bare product name is six characters and says nothing
to someone reading a search result, and Play's naming policy rules out the
usual fixes: no ranking claims, no emoji, no all caps, no "free" in the title.
A plain category suffix is what is left.

### Short description

    Write it down and you will be told. Free, no account, no subscription.

70 of 80 characters.

This is the app's own core promise, the line `onboarding_promise` already
shows on first run, and the listing agreeing word for word with the first
screen is worth more than a cleverer sentence.

`ROADMAP.md` drafts "A calm task app for Android whose reminders actually go
off. Free, no account, no subscription." That is 95 characters and does not
fit. "Actually go off" also reads as a swipe at the category rather than a
promise about this app, which is a tone `PRODUCT.md` principle 7 does not
support.

### Full description

Under 4000 characters. Play shows roughly the first three lines before the
"more" fold, so the promise and the price are both above it.

---

Catimo is a task app for Android with one job: telling you about your work at
the moment it matters. Free, with no account, no subscription and no ads.

If you write it down here, you will be told.

**Three lists, and no filing system**

Today answers "what should I do now". Inbox holds what you have not scheduled
yet. Upcoming shows what is coming. There is no project tree, no tags and no
folders to maintain, so you never have to remember which list you put
something in.

**Reminders built like alarms**

A reminder is scheduled as an exact alarm, and it survives the things that
usually lose one: the app closed, the screen off, the device restarted, the
clock or the time zone changed. Snooze it for ten minutes, an hour, this
evening or tomorrow morning, or complete the task, straight from the
notification without opening the app.

**A screen that checks the reminders for you**

Android and phone manufacturers can stop any app delivering on time, and most
task apps leave you to discover that when a reminder does not arrive. Catimo
has a Reminder health screen. It checks notification access, exact alarm
permission and battery restrictions, sends a test reminder when you ask, and
tells you afterwards if a real reminder arrived late or arrived silently. When
your phone's own settings are the problem, it opens the one that fixes it.

**Type it the way you would say it**

"Pay the invoice friday" schedules for Friday. "Call mum tomorrow 6pm"
schedules for tomorrow and sets a 6pm reminder. "Stretch in 20 minutes" sets a
reminder twenty minutes out. What was understood is marked in the field before
you save, and you can dismiss it if it read you wrong.

**Repeating tasks that come back**

Daily, weekly on the days you choose, monthly or yearly, ending on a date,
after a number of times, or never. Completing one occurrence creates the next.
A repeating task does not disappear when you finish it.

**Focus, for one task**

Start a session on a single task, with a time estimate or open ended. Pause it
and come back to it. One task at a time, and no queue.

**A home screen widget**

Today's tasks on your home screen, updated when the list changes. Tick one off
without opening the app.

**Your data stays on your phone**

No account, no sign-up, no servers. Catimo does not request internet
permission, so the app itself sends nothing anywhere. Your tasks live in a
database on the device. Back up to a JSON file you choose the location of, and
restore it on a new phone.

**Free, with nothing held back**

No subscription, no paid tier, no in-app purchases, no ads. Everything listed
here is in the app you install.

**Built for Android**

Material 3 throughout, dynamic color from your wallpaper, light and dark
themes, and screens that still work at large system font sizes.

What Catimo does not do: habits, streaks, points, productivity scores, teams,
or AI. The reward is getting the work done.

---

## Where each claim comes from

| Claim | Evidence |
| --- | --- |
| Exact alarms | `USE_EXACT_ALARM` in the manifest, `SCHEDULE_EXACT_ALARM` below API 33 |
| Survives restart, clock and time zone changes | `ReminderRecoveryReceiver`, on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` |
| Four snooze choices | `SnoozeOption`: ten minutes, an hour, this evening, tomorrow morning |
| Complete from the notification | `ReminderActionReceiver`, through the same `TaskCompletion` as the checkbox |
| Health screen checks three things | `reminder_health_check_notifications`, `_exact`, `_background` |
| It reports a late or silent arrival | `reminder_health_missed_title`, `_missed_silent_title` |
| It opens the manufacturer's own setting | `core/notification/DeviceSettingsRoute.kt` and the `<queries>` block |
| "friday", "tomorrow 6pm", "in 20 minutes" | `DateParser`, `CaptureParser`, `parseRelativeTime` |
| Recurrence options and end conditions | `RecurrenceUnit`, `RecurrenceEnd.Never / OnDate / AfterOccurrences` |
| Focus with an estimate or open ended, pause and resume | `ui/focus`, D-013 |
| Widget updates without opening the app | Phase 4 exit criteria, `ui/widget` |
| Backup to a file the user places | `ui/settings/BackupScreen.kt`, Storage Access Framework |
| Dynamic color, light and dark | `settings_dynamic_color`, `settings_theme_*` |
| No in-app purchases | No billing dependency, D-001 |

**"The app itself sends nothing anywhere" is worded carefully.** No `INTERNET`
permission is declared, which is a hard guarantee about Catimo's own code. It
is not a guarantee that no copy of the data exists off the device: see the
open question below.

### Claims deliberately not made

- **Nothing about never missing.** The product promise is internal. A listing
  that promises delivery on every device makes a claim `docs/decisions.md`
  D-009 already measured wrong on real hardware, and the Reminder health
  screen exists because the app cannot make it unilaterally.
- **Focus is not led with**, per D-004. It sits seventh, after the reminder
  material.
- **No full-screen alarm**, which is post-1.0 under D-038 and has never
  existed in the app.
- **No subtasks, lists, tablet layouts or Wear support.** All post-1.0 in
  `PRODUCT.md`.
- **No comparison to a named competitor.**

---

## Graphics

Nothing here exists yet. All of it needs a device or the emulator.

| Asset | Spec | Status |
| --- | --- | --- |
| App icon | 512 x 512 PNG, 32-bit, no alpha | TODO, from `ic_launcher` |
| Feature graphic | 1024 x 500 PNG or JPEG | TODO |
| Phone screenshots | 2 to 8, 9:16, 1080 x 1920 or larger | TODO |

### Screenshot shot list

In order, because the first two are what most people see. Shot on a clean
install with realistic tasks typed by hand, not fixture data. Light theme for
the first run, and at least one dark shot in the set.

1. **Today**, a few tasks, one of them overdue. The primary question the app
   answers.
2. **A reminder notification** on the lock screen or as a heads-up, showing
   the Done and Snooze actions. The core promise, visible.
3. **Quick Add** mid-type, with "call mum tomorrow 6pm" marked, so the parsing
   is shown rather than described.
4. **Reminder health**, in the Ready state with the three checks passing. The
   thing no competitor screenshot has.
5. **Upcoming**, showing the days ahead.
6. **The widget** on a home screen, alongside the wallpaper the dynamic color
   is drawn from.
7. **Focus**, a running session. Late, per D-004.
8. **Dark theme**, whichever screen reads best. Today is the safe choice.

Caption text over screenshots is optional. If it is used, keep it to the same
plain register as the app's own copy.

---

## Store settings

| Field | Value |
| --- | --- |
| App or game | App |
| Free or paid | Free |
| Category | Productivity |
| Tags | Task management, Reminders, To-do list, Personal organiser |
| Email address | TODO |
| Website | TODO, optional |
| Phone | Leave empty |
| Privacy policy URL | TODO, hosting `docs/privacy-policy.md` |

---

## Data safety form

The short answer is **no data collected and no data shared**, and the app has
no internet permission to do either with.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No |
| Is all of the user data encrypted in transit? | Not applicable, nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | Not applicable. Deleting the app deletes the data |

Two things that look like collection and are not. Tasks, notes, dates and
settings are stored on the device and never transmitted. A backup file is
written only when the user chooses Export and picks the location themselves,
which is the user moving their own file, not the developer receiving anything.

---

## Content rating and declarations

| Question | Answer |
| --- | --- |
| Category for the questionnaire | Productivity, not a game |
| Violence, sexuality, language, controlled substances | None |
| User-generated content shared with others | No |
| Ads | None, and the ads declaration is No |
| In-app purchases | None |
| Target audience | TODO, recommend 18 and over. Nothing in the app is aimed at children, and a younger band opts the listing into the Families policy and its extra requirements for no benefit |
| App access | All functionality is available with no restrictions. No login, no test credentials needed |
| Government app | No |
| Financial features | None |
| Health | No |
| News | No |

---

## Open question, for the developer rather than the code

**Android Auto Backup is on, and the listing copy accounts for it.**
`android:allowBackup="true"` is set, and `backup_rules.xml` and
`data_extraction_rules.xml` are both still the Android Studio templates with
every rule commented out. So the Room database and the app's preferences are
eligible for Google's cloud backup, and on a device signed into a Google
account a copy of a user's tasks can sit in their Drive. It is encrypted, and
on modern Android it is tied to the device PIN, but it is off the device.

That does not break anything above. The full description says "the app itself
sends nothing anywhere", which stays true, and the privacy policy discloses
the Auto Backup path by name. The Data safety form is also unaffected: Google
treats Auto Backup as the user's own backup rather than collection by the
developer.

It is worth a deliberate answer rather than a default one. Three options:

1. **Leave it on and disclose it**, which is what is drafted. Users get
   device-to-device migration for free.
2. **Exclude the database** in `data_extraction_rules.xml`, keeping settings
   but not task content in the cloud.
3. **Set `allowBackup="false"`**, making Catimo's own Export the only way data
   leaves, which is the strongest reading of local-first.

Two and three are code changes that reverse a shipped default and would need a
`docs/decisions.md` entry first. They also make Export the only migration
path, which raises the cost of someone losing a phone. Option one is the
recommendation on those grounds, but it is a product call.
