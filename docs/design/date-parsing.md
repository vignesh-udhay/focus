# Date parsing

Typed dates in the Task Details date fields. What is understood, what is not,
and why the line falls where it does.

`PRODUCT.md` lists "Natural-language date parsing" as a V1 feature and says
nothing further about it. Everything below is a decision, not a requirement.

**Partly superseded by D-011.** This document excludes times of day from the
vocabulary and says "tomorrow at 3pm" must be refused rather than filed. D-011
reverses that for Quick Add: a trailing time is now read and sets a reminder.
The reason given here for the exclusion, that "a task carries a day and no
time", stopped being true when Phase 1 shipped reminders. Everything else below
still stands, including the trailing-run rule, the rejection behaviour and the
vocabulary for days.

---

# Where it applies

Two places, on different terms.

**The date fields in Task Details** take the whole input. Typing into a date
field is unambiguously a request for a date, so nothing has to be inferred
about intent.

**Quick Add titles** are parsed at the end only, through `splitTrailingDate`.

An earlier version of this document said Quick Add titles were not parsed at
all. The reasoning was that the conventional implementation reads the title and
strips the phrase it matched, silently rewriting what the user typed: "Ship the
Monday report" would become "Ship the report" scheduled for Monday, and
`PRODUCT.md` does not authorise altering a title.

That objection is right about the conventional implementation and is what the
current design is built to avoid. Two things answer it:

**Only a trailing run of words is considered.** "Ship the Monday report" is
untouched, because "Monday" is not at the end. The doc's own counter-example is
a test case.

**The rewrite is visible before it happens.** The words about to be taken are
coloured as they are typed and named underneath in text, so the user sees the
split before saving rather than discovering it afterwards. A silent rewrite is
one that cannot be corrected; this one can be, by typing.

The reversal is deliberate. `PRODUCT.md` lists natural-language date parsing as
a V1 feature and asks that capture require almost no decisions. Quick Add is
the fastest path through capture, and a user who types "tomorrow" there has
made the decision already.

---

# The vocabulary

    today                    tomorrow
    monday .. sunday         next monday, this monday
    in 3 days                in 2 weeks
    4 september              september 4
    4 september 2026         september 4 2026
    on friday                by 4 september

Any form may be introduced by `on` or `by`, D-069. The preposition is part of
what is matched and part of what is marked. It is dropped in `parseDate`, so the
date fields and the Quick Add split inherit it together, and `by friday`
schedules for Friday rather than setting a due date: Quick Add writes one date
field, and a preposition switching which field a capture lands in is not
something the sheet has room to say.

Matching is case-insensitive, folded with `Locale.ROOT`. Commas are dropped,
runs of whitespace collapsed, and one sentence terminator is dropped from the
end, because voice typing punctuates what it hears and "tomorrow." was
unrecognisable, D-070. The relative forms take number words from the shared
table in `SpokenNumbers.kt` as well as digits: "in three days", "in a week". Month names are accepted in full or as their
first three letters, in either order relative to the day.

Deliberately absent: `next week`, `yesterday`, numeric dates, and any unit
larger than a week.

**A preset button is allowed to offer what this list refuses**, and Task Details
does. `next week` is excluded because English does not agree on which week it
means, which is an argument about ambiguous text rather than about the concept.
A button carries one defined meaning and so does not inherit the ambiguity.
D-018 records the rule and fixes the meaning of the two presets that are not in
this vocabulary: `This weekend` is the coming Saturday and `End of week` is the
coming Friday.

Times of day were on this list and are not any more; see D-011. A trailing time
is read in Quick Add and sets a reminder, which is shown as a dismissible chip
rather than applied silently. That vocabulary lives in `CaptureParser.kt` and
D-069 last added to it: `noon` and `midday` are 12:00, `midnight` is 00:00,
`tonight` is 20:00, `this evening` is 18:00, and `in 2 hours` or `in 30 minutes`
sets a reminder counted from the moment of typing. An offset and a named day
cannot both be honoured, so a capture asking for both takes neither.

D-070 added the transcript forms, so dictation through the keyboard's mic
parses like typing: an hour said as a word with its meridiem ("six pm"), number
words in offsets ("in two hours", "in an hour"), a punctuated meridiem
("6 p.m."), and the trailing full stop voice typing adds. "at six" with no
meridiem stays refused, because nothing in it says which of the two sixes, and
D-070 records why neither candidate rule survives "friday at six". The same
refusal covers a single-digit hour with a separator: dictation writes a spoken
"at six" as "6:00", which is not a 24-hour time anyone typed, so the 24-hour
form requires both hour digits and "6:00" stays in the title while "06:00" and
"18:00" read. D-070's addendum has the measurement that forced this.

---

# The rules

**A weekday is the next one strictly after today.** "monday" typed on a Monday
is a week away. "next monday" and "this monday" mean the same thing.

English usage disagrees with itself about which week "next Monday" points at,
and resolving that properly means knowing when a week starts, which differs by
country. One rule that is never more than seven days out is better than a rule
that is sometimes seven days wrong.

**A month and day without a year is the next time it comes round.** "2 january"
typed in December is next year. "29 february" finds the next leap year rather
than being nudged to the 28th.

**With a year it is taken literally.** "29 february 2027" is not a date, so it
is rejected rather than quietly stored as the 28th.

**No supported input ever resolves to a day before today.** Nothing can be put
into Today's overdue band by typing.

---

# Rejection

Three outcomes, not two. Empty text means the task has no date. Text that is
not understood is an error: the field shows it, and Save is blocked, on the
same terms as an unusable duration. Nothing is guessed.

**The whole input has to match.** Nothing is extracted from a longer string,
which is what makes "tomorrow at 3pm" unrecognised rather than filed as
tomorrow. A task carries a day and no time, so accepting the day and dropping
the hour would tell the user their 3pm was understood when nothing about it was
stored.

**Numeric dates are refused on purpose.** 03/09 is March to an American reader
and September to a British or Indian one, and the app has no way to know which
was meant. Spelling the month out removes the question.

---

# Language

English only, matched against the `DayOfWeek` and `Month` enum constants rather
than locale display names, so the vocabulary is identical on every device and
does not shift with a locale data update.

That matches the rest of the app, which has one string resource directory. The
resolved date shown under the field is formatted in the reader's own locale;
only the vocabulary is English.

Case folding uses `Locale.ROOT` rather than the default locale. On a Turkish
device the default folds a capital I to a dotless lower-case i, and "In 2 days"
would stop being understood.

---

# Reading a day off a title

    splitTrailingDate(text: String, today: LocalDate): TitleWithDate

Walks the trailing words longest-first, capped at four because no supported
form is longer than "on 4 september 2026", and hands each candidate to
`parseDate` **whole**. Everything `parseDate` refuses is refused here too, so
the two cannot drift apart and there is no second vocabulary to maintain.

That inheritance is what keeps "Write the report tomorrow at 3pm" intact. "at
3pm" is not a date, "tomorrow at 3pm" is not one either, and a task carries no
time of day, so nothing here could store the hour. The title keeps the words
and the user sets a day in Task Details.

**Never the whole title.** A title that is nothing but a day stays a title:
"tomorrow" on its own is more likely a task someone meant to finish naming than
a dateless reminder, and capturing it as an empty title would lose what they
typed.

`TitleWithDate` also reports where the day began in the original string, so the
field can mark exactly the words it is about to take. The index is into the text
as typed, not into the trimmed title, which is what keeps the marking from
drifting when there is leading whitespace.

The date wins over the screen's default. Today captures for today, Inbox
captures without a day, and a title that named a day overrides both — an Inbox
capture that says "friday" has had the deciding done, which is the one decision
Inbox exists to defer.

---

# Where it lives

`core/domain/DateParser.kt`, a pure function beside `TaskQueries`:

    parseDate(text: String, today: LocalDate): ParsedDate

It takes the day rather than reading a clock, exactly as every query does, so
the app keeps one answer to what day it is and every case is testable without a
device. No Android imports, no Compose, no new dependency: `java.time` covers
the whole vocabulary and is available unconditionally at minSdk 29.

---

# Verification

`SplitTrailingDateTest` covers the title split: trailing days of every shape,
the longest match winning, a day in the middle being left alone, a time of day
being refused rather than dropped, a title that is only a day, and the offsets
staying valid against the original string.

`CaptureParserTest` covers the Quick Add split, which reads a time as well as a
day: every time form including the five word times, the offsets and what they
refuse, the day and offset pair that parses to nothing, the mark, and what
dismissing the chip drops.

`DateParserTest` covers the vocabulary, the weekday and month-day boundaries,
year and leap-year rolling, case and locale folding, every rejection above, and
a property check walking a full year and asserting that no supported input ever
resolves to the past.

Only the Android integration is checked by hand: that typed text reaches the
saved task, that unrecognised text blocks Save, that the picker still writes
the field, and that both fields survive font scaling.
