package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.vignesh.focuslist.FocuslistApplication
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDarkColorScheme
import com.vignesh.focuslist.core.design.FocuslistLightColorScheme
import com.vignesh.focuslist.core.domain.TodayBand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/**
 * Where text begins and ends, on both edges.
 *
 * Named because it was previously spelled 18dp in the header, 18dp in the empty
 * states and 24dp at the end of a row, so nothing lined up with anything and
 * each new element picked whichever number was nearest in the file.
 *
 * 24 is the number the board already committed to: a row's duration column ends
 * 24dp from the edge, and the add button's 32dp circle sits centred in a 48dp
 * target with a 16dp trailing spacer, which puts its visible edge at 24 as well.
 */
private val ContentInset = 24.dp

/**
 * Where a filled surface's edge sits, and where a 48dp touch target begins.
 *
 * Smaller than [ContentInset] on purpose, and the difference is the board's
 * checkbox inset: a target that starts at 8 carries its glyph 15dp inside
 * itself, so the glyph lands at 23, one dp inside the text column above it. That
 * is below perception and it is what lets the target keep all 48dp of itself
 * while its contents still read as one column with the text.
 *
 * Every row's checkbox target starts here, which is the one left edge the rows
 * share. Band headers do not: they sit on [ContentInset] with the widget title,
 * because a heading belongs in the text column rather than in the target column.
 */
private val SurfaceInset = 8.dp

/**
 * A band header's id in the lazy list.
 *
 * Rows are keyed on their task's hash code, which is an `Int`, so anything
 * outside that range cannot collide with one. Headers take the top of the range
 * downwards, one per band, so a band keeps its identity across a refresh and the
 * list holds its scroll position.
 *
 * `Long.MAX_VALUE` and not `Long.MIN_VALUE`, which reads like the obvious pick
 * for "no task could be this" and is in fact `LazyListScope.UnspecifiedItemId`.
 * Passing it asks Glance to invent an id, which is the opposite of what a stable
 * id is for. The lead card was spelled that way from D-043 until D-045, and the
 * lead card is gone as of D-049.
 */
private val TodayBand.itemId: Long get() = Long.MAX_VALUE - ordinal

private val WidgetFallbackColors = ColorProviders(
    light = FocuslistLightColorScheme,
    dark = FocuslistDarkColorScheme
)

class FocuslistWidget : GlanceAppWidget() {

    /**
     * One layout, built once, per D-043.
     *
     * Nothing reads the widget's size any more. The rows are a `LazyColumn`, so
     * the platform fills the height and scrolls the remainder, and the layout
     * stretches to whatever the launcher gives it.
     *
     * This supersedes D-041's `SizeMode.Exact` one entry later, and that is not
     * a retraction: `Exact` was the correct answer while `rowCapacity` existed,
     * and finding out it had never been given a real height is what exposed the
     * measuring as the liability. With no reader, `Exact` would rebuild
     * RemoteViews on every resize to produce a layout that does not depend on
     * the result.
     */
    override val sizeMode: SizeMode = SizeMode.Single

    /**
     * Observed, not captured, per D-045. Drawn before it is read, per D-051.
     *
     * This function body runs once per Glance session rather than once per
     * update, and a session lives 45 seconds past its first composition. A
     * snapshot read here was therefore the only data the widget had for that
     * whole window: `updateAll` forces a recomposition, and recomposing
     * re-rendered the same captured value. Collecting the stream inside
     * `provideContent` is what lets a change during the session reach the
     * screen.
     *
     * **Nothing is awaited before `provideContent`, and that is deliberate.**
     * Until it is called, `AppWidgetSession` emits `IgnoreResult()` and publishes
     * nothing, and the 45-second clock has not started either: the only timer
     * that can be running is the five-second idle one. A phone that signals idle
     * during a slow first read kills the session before it draws, and Glance
     * reports that as a successful worker. With `updatePeriodMillis` at zero,
     * nothing retries it.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val application = context.applicationContext as FocuslistApplication
        val snapshots = widgetSnapshots(
            tasks = application.taskRepository.observeTasks(),
            // The app's one answer, so a rollover past midnight reaches a live
            // session rather than waiting for it to expire.
            today = application.currentDay.today,
            completion = { tasks, today ->
                application.widgetInteractions.completionFor(tasks, today)
            }
            // Room brings its own thread; the completion lambda reads prefs.
        ).flowOn(Dispatchers.IO)

        provideContent {
            val snapshot by snapshots.collectAsState(null)

            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    WidgetFallbackColors
                }
            ) {
                val model = snapshot?.let { read ->
                    focuslistWidgetModel(
                        tasks = read.tasks,
                        today = read.today,
                        completion = read.completion
                    )
                } ?: FocuslistWidgetModel(WidgetBody.Loading)

                FocuslistWidgetContent(model)
            }
        }
    }
}

class FocuslistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocuslistWidget()
}

@Composable
private fun FocuslistWidgetContent(model: FocuslistWidgetModel) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(R.dimen.widget_corner_radius)
            // So the last row does not sit on the widget's bottom edge. The
            // header supplies its own 60dp at the top; nothing was supplying
            // anything at the bottom, and a filled container with content
            // against its edge reads as clipped rather than as full.
            .padding(bottom = SurfaceInset)
            .appWidgetBackground()
            .clickable(actionStartActivity(widgetLaunchIntent(context, WidgetLaunchCommand.Today)))
    ) {
        WidgetHeader(context)

        when (val body = model.body) {
            // The header and nothing under it, until the first snapshot lands.
            // Drawn rather than waited for, per D-051: a session that has
            // published nothing can be timed out having drawn nothing, and the
            // launcher then keeps its loading layout with nothing to retry it.
            WidgetBody.Loading -> Unit

            WidgetBody.EverythingDone -> WidgetEmptyBody(
                text = context.getString(R.string.widget_all_done)
            )
            // The headline alone. Today's supporting line points at the Inbox,
            // which is a place the launcher cannot show, and EverythingDone
            // above already answers an end state in one line.
            WidgetBody.NothingScheduled -> WidgetEmptyBody(
                text = context.getString(R.string.today_empty_headline)
            )
            is WidgetBody.Sections -> WidgetSectionsBody(body)
        }
    }
}

@Composable
private fun WidgetHeader(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.today_title),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            // On the text column, not 18dp. It was the only element in the
            // widget at 18, so the title started six dp inside a line nothing
            // else stood on, while the add button opposite it was already
            // landing on 24.
            modifier = GlanceModifier.defaultWeight().padding(start = ContentInset)
        )
        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .clickable(actionStartActivity(widgetLaunchIntent(context, WidgetLaunchCommand.Add))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_add),
                    contentDescription = context.getString(R.string.widget_add_task),
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface)
                )
            }
        }
        Spacer(GlanceModifier.width(16.dp))
    }
}

@Composable
private fun ColumnScope.WidgetEmptyBody(text: String, supporting: String? = null) {
    Box(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = ContentInset),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text = text,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Today's bands, as a collection rather than a column. D-043, D-049.
 *
 * `LazyColumn` is a `ListView` in RemoteViews terms, so every outstanding task
 * is handed over and the platform draws what fits and scrolls the rest.
 *
 * **Each band contributes a header item and then its rows**, in the order
 * `todaySections` produced them, which is the order the rows were already in.
 * Before D-049 this was one unlabelled run under a lead card that explained the
 * top of it; with the card gone, nothing explained the run at all.
 *
 * The list measures to its content rather than filling, per D-047, so the space
 * below a short list belongs to the root and keeps opening Today.
 */
@Composable
private fun ColumnScope.WidgetSectionsBody(body: WidgetBody.Sections) {
    LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
        body.sections.forEach { section ->
            item(itemId = section.band.itemId) { WidgetBandHeader(section.band) }

            // Keyed on the task, so the list holds its scroll position across a
            // refresh instead of jumping to the top every time Room emits.
            items(
                items = section.rows,
                itemId = { row -> row.task.id.hashCode().toLong() }
            ) { row ->
                WidgetTaskRow(row, body.today)
            }
        }
    }
}

/**
 * A band label, in Today's words.
 *
 * **The strings are Today's own**, because the widget naming these groups
 * differently would make them look like different groups.
 *
 * **Bold at 12 rather than quieter at 14**, which is the opposite of how Today
 * draws them. Today can reach for `onSurfaceVariant`; D-031 rules colour and
 * opacity out here on the grounds that a widget's hierarchy has to survive
 * whatever wallpaper is behind it, leaving size and weight. Smaller and heavier
 * than a row title reads as a label rather than as a louder row.
 *
 * Aligned to [ContentInset] with the widget's title, not to the row titles
 * inset past their checkboxes, so the headings sit in one column with "Today".
 */
@Composable
private fun WidgetBandHeader(band: TodayBand) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(start = ContentInset, bottom = 2.dp)
            // **Its own action, because it is inside the collection.** The root
            // carries "anywhere else opens Today", and `AbsListView` consumes
            // every touch within its bounds before the root can see it. Rows
            // have their own targets and so did not notice; a header had none
            // and was inert until this was added.
            .clickable(actionStartActivity(widgetLaunchIntent(context, WidgetLaunchCommand.Today))),
        // Bottom, so the space in the item sits above the label and groups it
        // with the rows beneath rather than with the band it follows.
        verticalAlignment = Alignment.Vertical.Bottom
    ) {
        Text(
            text = context.getString(band.labelRes),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

/**
 * Completed is absent from the widget, per D-049, and named rather than thrown
 * so a band added above cannot crash a home screen.
 */
private val TodayBand.labelRes: Int
    get() = when (this) {
        TodayBand.OVERDUE -> R.string.today_section_overdue
        TodayBand.NO_TIME_SET -> R.string.today_section_no_time_set
        TodayBand.LATER_TODAY -> R.string.today_section_later_today
        TodayBand.COMPLETED -> R.string.today_section_completed
    }

@Composable
private fun WidgetTaskRow(row: WidgetRow, today: LocalDate) {
    val context = LocalContext.current
    val text = widgetTaskText(context, row.task, today)
    val checkboxAction = if (row.justCompleted) {
        actionRunCallback<UndoWidgetTaskAction>(
            actionParametersOf(WidgetTaskIdKey to row.task.id)
        )
    } else {
        actionRunCallback<CompleteWidgetTaskAction>(
            actionParametersOf(WidgetTaskIdKey to row.task.id)
        )
    }
    val bodyDescription = buildList {
        add(text.title)
        text.metadata?.let(::add)
        text.duration?.spoken?.let(::add)
    }.joinToString(". ")

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(if (text.metadata == null) 48.dp else 56.dp)
            // The board starts the 48dp checkbox target at x=8. Its title
            // then begins at x=58 after the target and the 2dp gap.
            .padding(start = SurfaceInset),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        WidgetCheckbox(
            checked = row.justCompleted,
            action = checkboxAction,
            description = context.getString(
                if (row.justCompleted) R.string.task_row_mark_incomplete
                else R.string.task_row_mark_complete,
                row.task.title
            ),
            hasSupportingText = text.metadata != null
        )
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(
                    if (row.justCompleted) checkboxAction
                    else actionStartActivity(
                        widgetLaunchIntent(context, WidgetLaunchCommand.TaskDetails(row.task.id))
                    )
                )
                .semantics { contentDescription = bodyDescription },
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = text.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (row.justCompleted) TextDecoration.LineThrough else null
                ),
                maxLines = 1
            )
            text.metadata?.let { metadata ->
                Text(
                    text = metadata,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        }
        text.duration?.let { duration ->
            Text(
                text = duration.text,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End
                ),
                maxLines = 1,
                modifier = GlanceModifier
                    // The board's fixed trailing column ends at x=340,
                    // leaving 24dp to the widget edge.
                    .width(70.dp)
                    .semantics { contentDescription = duration.spoken }
            )
        }
        Spacer(GlanceModifier.width(ContentInset))
    }
}

@Composable
private fun WidgetCheckbox(
    checked: Boolean,
    action: androidx.glance.action.Action,
    description: String,
    uncheckedColor: androidx.glance.unit.ColorProvider = GlanceTheme.colors.onPrimaryContainer,
    hasSupportingText: Boolean = false
) {
    val context = LocalContext.current
    CheckBox(
        checked = checked,
        onCheckedChange = action,
        text = "",
        // **Both colours, not the one this process happens to be in.** D-050.
        //
        // `CheckedUncheckedColorProvider` rejects resource-backed providers,
        // which is what dynamic Glance roles are, so they cannot be handed over
        // as they arrive. Resolving them to a single `Color` satisfied that and
        // cost the other half of the answer: a fixed colour has no night
        // variant, so Glance wrote the same `ColorStateList` into both the day
        // and the night slot and the checkbox stopped following the launcher
        // while every other colour in the widget kept following it.
        //
        // A `DayNightColorProvider` is the form the same check explicitly
        // allows, and [dayNight] builds one by resolving the role against both
        // configurations rather than against this one.
        colors = CheckboxDefaults.colors(
            checkedColor = GlanceTheme.colors.primary.dayNight(context),
            uncheckedColor = uncheckedColor.dayNight(context)
        ),
        modifier = GlanceModifier
            .size(48.dp)
            // RemoteViews puts its 32dp native checkbox at the leading edge of
            // the 48dp target. The board's Material checkbox inset is 15dp;
            // applying that inside the target puts the glyph at x=23 while
            // keeping all 48dp clickable. A bare row centres both title and
            // glyph, while a two-line row leaves the glyph at the target's top
            // so it aligns with the title rather than the title-plus-metadata
            // block's centre.
            .padding(start = 15.dp, top = if (hasSupportingText) 0.dp else 7.dp)
            .semantics { contentDescription = description },
        maxLines = 1
    )
    Spacer(GlanceModifier.width(2.dp))
}

/**
 * The same colour role, resolved for both day and night. D-050.
 *
 * Glance emits most colours as a day/night pair and lets the launcher pick,
 * which is why the widget's text and background follow the system while a
 * colour resolved here does not: `ColorProvider.getColor(context)` reads
 * `context.resources.configuration.uiMode`, and that is the app process at
 * composition time, not the launcher at draw time. The two agree until the
 * system changes mode without the widget being rebuilt, and then the resolved
 * colour is a day colour on a night surface or the reverse.
 *
 * Resolving twice against a configuration-corrected context gives back the pair
 * Glance wanted, which its own `resolveCheckedColor` does the same way.
 */
private fun androidx.glance.unit.ColorProvider.dayNight(
    context: Context
): androidx.glance.unit.ColorProvider = ColorProvider(
    day = getColor(context.withNightMode(false)),
    night = getColor(context.withNightMode(true))
)

/**
 * [context] with the night bits set and everything else left alone.
 *
 * Copied from the real configuration rather than built empty, because a dynamic
 * colour is resolved against the whole of it and a blank `Configuration` would
 * drop the density and locale the resource lookup needs.
 */
private fun Context.withNightMode(night: Boolean): Context {
    val configuration = Configuration(resources.configuration)
    configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return createConfigurationContext(configuration)
}
