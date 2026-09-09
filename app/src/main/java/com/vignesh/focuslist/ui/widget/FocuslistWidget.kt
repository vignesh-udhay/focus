package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.vignesh.focuslist.core.domain.FocusNow
import com.vignesh.focuslist.core.domain.FocusNowReason
import com.vignesh.focuslist.core.domain.StoredFocusSession
import com.vignesh.focuslist.data.local.WidgetCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

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
 * The lead card takes the same 8, so its edge and the rows' targets start
 * together, the way `today-screen.md` has the Focus now card and the bands share
 * one left edge.
 */
private val SurfaceInset = 8.dp

private val WidgetFallbackColors = ColorProviders(
    light = FocuslistLightColorScheme,
    dark = FocuslistDarkColorScheme
)

private data class WidgetSnapshot(
    val tasks: List<com.vignesh.focuslist.core.domain.Task>,
    val today: LocalDate,
    val storedFocus: StoredFocusSession?,
    val completion: WidgetCompletion?
)

class FocuslistWidget : GlanceAppWidget() {

    /**
     * The widget's real dimensions, per D-041.
     *
     * This was `SizeMode.Responsive` over the two board sizes, under which
     * `LocalSize` reports the matched member of the declared set rather than
     * what the launcher actually gave the widget. D-036 had already replaced the
     * breakpoint table with arithmetic over the reported height, on the argument
     * that a dragged widget is rarely either declared size, and then fed that
     * arithmetic one of two constants. A widget with room for five rows drew
     * three.
     *
     * **The rule the old mode was protecting still holds**: row count is the
     * only thing size may affect. `Exact` does not weaken it, it only makes the
     * number true. The guard is that `rowCapacity` is the sole reader of this
     * size, and any second reader is the thing to refuse.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val application = context.applicationContext as FocuslistApplication
        val snapshot = withContext(Dispatchers.IO) {
            val tasks = application.taskRepository.observeTasks().first()
            val today = LocalDate.now()
            WidgetSnapshot(
                tasks = tasks,
                today = today,
                storedFocus = application.focusSessionStore.current,
                completion = application.widgetInteractions.completionFor(tasks, today)
            )
        }

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    WidgetFallbackColors
                }
            ) {
                val model = focuslistWidgetModel(
                    tasks = snapshot.tasks,
                    today = snapshot.today,
                    now = LocalDateTime.now(),
                    storedFocus = snapshot.storedFocus,
                    completion = snapshot.completion,
                    // The height this widget actually has, per D-036, and since
                    // D-041 that is finally true rather than the nearer of two
                    // declared sizes. Width has no say in how many rows fit, and
                    // this is the only place the size is read at all.
                    heightDp = LocalSize.current.height.value,
                    fontScale = context.resources.configuration.fontScale
                )

                FocuslistWidgetContent(
                    model = model,
                    today = snapshot.today,
                    storedFocus = snapshot.storedFocus
                )
            }
        }
    }
}

class FocuslistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocuslistWidget()
}

@Composable
private fun FocuslistWidgetContent(
    model: FocuslistWidgetModel,
    today: LocalDate,
    storedFocus: StoredFocusSession?
) {
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
            WidgetBody.EverythingDone -> WidgetEmptyBody(
                text = context.getString(R.string.widget_all_done)
            )
            // The headline alone. Today's supporting line points at the Inbox,
            // which is a place the launcher cannot show, and EverythingDone
            // above already answers an end state in one line.
            WidgetBody.NothingScheduled -> WidgetEmptyBody(
                text = context.getString(R.string.today_empty_headline)
            )
            is WidgetBody.Tasks -> WidgetTasksBody(
                body = body,
                today = today,
                storedFocus = storedFocus
            )
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

@Composable
private fun WidgetTasksBody(
    body: WidgetBody.Tasks,
    today: LocalDate,
    storedFocus: StoredFocusSession?
) {
    body.lead?.let { WidgetLeadCard(it, today, storedFocus) }
    body.rows.forEach { row -> WidgetTaskRow(row, today) }
    if (body.hiddenCount > 0) {
        val context = LocalContext.current
        Text(
            text = context.resources.getQuantityString(
                R.plurals.widget_more_tasks,
                body.hiddenCount,
                body.hiddenCount
            ),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 12.sp
            ),
            maxLines = 1,
            // On the row titles it stands in for, not on the text column. It is
            // a row that did not fit rather than a label heading the group, so
            // it lines up with the titles above it: the target, then its 2dp
            // gap. `today-screen.md` draws the same distinction the other way
            // round for a band label, which heads a group and so sits at its
            // edge instead.
            modifier = GlanceModifier.padding(start = SurfaceInset + 50.dp, top = 4.dp)
        )
    }
}

@Composable
private fun WidgetLeadCard(
    lead: FocusNow,
    today: LocalDate,
    storedFocus: StoredFocusSession?
) {
    val context = LocalContext.current
    val text = widgetTaskText(context, lead.task, today)
    val completeAction = actionRunCallback<CompleteWidgetTaskAction>(
        actionParametersOf(WidgetTaskIdKey to lead.task.id)
    )
    val taskAction = actionStartActivity(
        widgetLaunchIntent(context, WidgetLaunchCommand.TaskDetails(lead.task.id))
    )

    // **The inset is on this Box, and it used to be on the card itself.** Glance
    // takes `padding` as a view's own padding rather than as a margin, so
    // `.padding(horizontal = 8.dp).background(surface)` on the card put the 8dp
    // *inside* it and drew the surface edge to edge. On a launcher that reads as
    // the card having escaped the widget: its corners sit outside the rounded
    // container that is supposed to hold them.
    //
    // A parent carrying the padding is how Glance expresses a margin. The card's
    // own contents are unchanged and still land where the board puts them,
    // because they were already written against an 8dp offset.
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            // Clear of the header, which is a filled surface arriving directly
            // under a title with no separation of its own.
            .padding(start = SurfaceInset, end = SurfaceInset, bottom = SurfaceInset)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(86.dp)
                .background(GlanceTheme.colors.surface)
                .cornerRadius(R.dimen.widget_inner_radius),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            WidgetCheckbox(
                checked = false,
                action = completeAction,
                description = context.getString(R.string.task_row_mark_complete, lead.task.title),
                uncheckedColor = GlanceTheme.colors.onSurface
            )
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(taskAction)
            ) {
                Text(
                    text = widgetLeadReason(context, lead, storedFocus),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = text.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 2.dp)
                )
            }
            if (lead.reason == FocusNowReason.ResumePaused) {
                Box(
                    modifier = GlanceModifier
                        .width(80.dp)
                        .height(32.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(16.dp)
                        .clickable(
                            actionStartActivity(
                                widgetLaunchIntent(
                                    context,
                                    WidgetLaunchCommand.ResumeFocus(lead.task.id)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.widget_resume),
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }
                // Lands the pill's edge on `ContentInset`, measured from the
                // widget rather than from the card, since the card itself is
                // already `SurfaceInset` in.
                Spacer(GlanceModifier.width(ContentInset - SurfaceInset))
            } else {
                text.duration?.let { duration ->
                    Text(
                        text = duration.text,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .width(54.dp)
                            .semantics { contentDescription = duration.spoken }
                    )
                }
                Spacer(GlanceModifier.width(ContentInset - SurfaceInset))
            }
        }
    }
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
        // Dynamic Glance roles are resource-backed providers. CheckBoxColors
        // rejects those providers when checked and unchecked colors are
        // supplied separately, so resolve them in the app process first and
        // pass the concrete colors. This keeps dynamic colour while allowing
        // the first non-empty widget state to render.
        colors = CheckboxDefaults.colors(
            checkedColor = GlanceTheme.colors.primary.getColor(context),
            uncheckedColor = uncheckedColor.getColor(context)
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
