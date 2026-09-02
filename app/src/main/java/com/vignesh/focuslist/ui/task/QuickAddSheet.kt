package com.vignesh.focuslist.ui.task

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.KeyboardOptions
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.TitleWithDate
import com.vignesh.focuslist.core.domain.splitTrailingDate
import com.vignesh.focuslist.ui.component.scheduledDateLabel
import java.time.LocalDate

/**
 * Quick Add.
 *
 * One field and one action, because capturing a task should require almost no
 * decisions. Everything else about the task is decided later.
 *
 * The one thing the field does read is a day off the end of what was typed, so
 * "Call the plumber tomorrow" captures as a task for tomorrow without opening
 * anything. The words that will be taken are coloured as they are typed and
 * named underneath, because a silent split is one that cannot be corrected: a
 * user who meant those words literally has to be able to see them going.
 *
 * The colour is the quieter half of that signal and never the only one. The
 * supporting line carries the same fact in text, which is what a screen reader
 * announces and what survives a colour-blind reading.
 *
 * The sheet owns only the text being typed. Whether it is open, and what
 * happens on save, belong to the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (TitleWithDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Recomputed as the user types: it decides both what the field marks and
    // what Save hands over, so the two can never disagree.
    val parsed = splitTrailingDate(title, today)

    val dayStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)
    val markTheDay = remember(parsed.dateStart, dayStyle) {
        VisualTransformation { text ->
            val start = parsed.dateStart
            val marked = if (start == null || start > text.length) {
                AnnotatedString(text.text)
            } else {
                buildAnnotatedString {
                    append(text.text.substring(0, start))
                    withStyle(dayStyle) { append(text.text.substring(start)) }
                }
            }

            // Nothing is added or removed, so positions are unchanged and the
            // cursor lands where the user put it.
            TransformedText(marked, OffsetMapping.Identity)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            // One field: there is no half-height state worth stopping at.
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.quick_add_title_label)) },
                singleLine = true,
                visualTransformation = markTheDay,
                supportingText = parsed.date?.let { day ->
                    {
                        Text(
                            stringResource(
                                R.string.quick_add_scheduled_for,
                                scheduledDateLabel(day, today)
                            )
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Button(
                onClick = { onSave(parsed) },
                // The title is the one thing a task cannot do without, and it
                // is the title left after the day is taken that has to exist.
                enabled = parsed.title.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = FocuslistSpacing.md)
            ) {
                Text(stringResource(R.string.quick_add_save))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
