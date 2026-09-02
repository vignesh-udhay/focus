package com.vignesh.focuslist.ui.playground

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.ui.component.TaskRow
import com.vignesh.focuslist.ui.theme.FocuslistTheme

/**
 * A temporary harness for evaluating [TaskRow] as one segmented collection.
 *
 * It is deliberately not connected to application navigation and holds its own
 * sample state, so completion can be toggled while judging the visual result.
 */
@Composable
fun TaskRowPlayground(modifier: Modifier = Modifier) {
    val tasks = remember { PlaygroundTasks.toMutableStateList() }

    Surface(
        modifier = modifier.fillMaxSize(),
        // Segments use the default segmented container colour, which is
        // `surface`, so the screen behind them takes a container role.
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = FocuslistSpacing.md,
                vertical = FocuslistSpacing.xs
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                TaskRow(
                    title = task.title,
                    isCompleted = task.isCompleted,
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = tasks.size
                    ),
                    onToggleComplete = { completed ->
                        tasks[index] = task.copy(isCompleted = completed)
                    },
                    onClick = {},
                    metadata = task.metadata
                )
            }
        }
    }
}

private data class PlaygroundTask(
    val id: Int,
    val title: String,
    val metadata: List<String> = emptyList(),
    val isCompleted: Boolean = false
)

private val PlaygroundTasks = listOf(
    PlaygroundTask(
        id = 1,
        title = "Finish the landing page",
        metadata = listOf("Today", "45 min")
    ),
    PlaygroundTask(
        id = 2,
        title = "Reply to Priya about the roadmap"
    ),
    PlaygroundTask(
        id = 3,
        title = "Draft the accessibility checklist for the task list, covering " +
            "TalkBack, font scaling and touch targets",
        metadata = listOf("Today", "30 min", "Website")
    ),
    PlaygroundTask(
        id = 4,
        title = "Book the dentist",
        metadata = listOf("Tomorrow")
    ),
    PlaygroundTask(
        id = 5,
        title = "Send the sprint summary",
        metadata = listOf("Today", "Work"),
        isCompleted = true
    ),
    PlaygroundTask(
        id = 6,
        title = "Pick up the parcel",
        isCompleted = true
    )
)

@Preview(name = "Light", heightDp = 560)
@Preview(name = "Dark", heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TaskRowPlaygroundPreview() {
    FocuslistTheme(dynamicColor = false) {
        TaskRowPlayground()
    }
}
