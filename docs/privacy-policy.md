# Privacy policy for Tellcat

Last updated: 12 October 2026.

Tellcat is a task and reminder app for Android, published on Google Play under
the developer name Tellcat.

This policy is short because the app does very little with your data. It has
no account, no servers and no analytics.

## The short version

Tellcat does not collect, transmit or share anything you put into it. Your
tasks stay on your phone.

## What Tellcat stores, and where

Everything you enter is stored in a database inside the app's own private
storage on your device:

- task titles and notes
- scheduled dates, due dates and reminder times
- estimated durations and repeat rules
- completed tasks in the Logbook
- your theme and dynamic colour settings
- a record of when reminders were delivered, used by the Reminder health
  screen to tell you if one arrived late

So that a reminder still arrives after your phone restarts and before you
unlock it, Tellcat also keeps the title and time of each upcoming reminder in
the part of its private storage that Android makes readable from startup. That
copy is protected by your device's encryption rather than by your screen lock,
holds nothing else, and is replaced every time your reminders change.

No other app on your device can read this storage. Uninstalling Tellcat deletes
all of it.

## What Tellcat does not do

- It does not ask you to create an account or sign in.
- It does not have any server. There is nothing to send data to.
- It does not request the Android internet permission, so the app cannot make
  a network connection at all.
- It contains no analytics, no crash reporting, no advertising and no
  third-party tracking libraries.
- It does not read your contacts, calendar, location, photos, files, call log
  or microphone.

## Permissions, and why each one exists

- **Notifications.** To show a reminder when one is due. Requested the first
  time it is needed rather than at launch.
- **Alarms & reminders.** To make a reminder arrive at the time you set
  instead of whenever the system next feels like waking the app. Android asks
  you for this one in its own settings, and Tellcat takes you there the first
  time you set a reminder. You can turn it off at any time; reminders still
  arrive, but may be late.
- **Run at startup.** Android discards every scheduled alarm when the device
  restarts. Without this, reminders set before a restart would never arrive.
- **Package visibility.** Tellcat can check whether a small list of known
  manufacturer battery-management apps is present, so the Reminder health
  screen can send you to the right settings page when your phone is blocking
  delivery. Nothing is read from those apps, and no list of your installed
  apps is collected.

## Backup files you create

Settings has an Export option that writes your tasks and settings to a JSON
file. You choose where that file goes through Android's own file picker.

Once you have saved it, the file is yours and is outside Tellcat's control. If
you place it in a cloud folder, or send it to yourself, it goes wherever you
put it. Tellcat never uploads it, and never reads a backup file you have not
picked.

## Android's own backup

Tellcat participates in Android Auto Backup, the system feature that copies an
app's data so a new phone can restore it. When it is switched on and you are
signed into a Google account, Android may copy Tellcat's data to your personal
Google Drive as part of your device backup. That copy is made by Android
rather than by Tellcat, it is encrypted, and on current Android versions it is
protected by your device PIN or password so that Google cannot read it. It
does not count against your Drive storage.

You can turn this off for all apps in your device's Settings, under System,
then Backup. Tellcat's own Export remains available either way.

## Children

Tellcat is not directed at children and does not knowingly collect anything
from anyone, of any age.

## Changes to this policy

If this policy changes, the updated version will be posted at this address and
the date at the top will change. Since the app has no way to contact you, that
page is the only notice.

## Contact

Questions about this policy, or about privacy in Tellcat:

hello@tellcat.app
