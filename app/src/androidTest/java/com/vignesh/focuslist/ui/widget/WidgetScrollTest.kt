package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the rows really are a collection, per D-043.
 *
 * The model test can say the widget was handed every task. It cannot say what
 * Glance did with them, and that is the half that decides whether the user can
 * scroll: a `LazyColumn` is supposed to compile to a `ListView`, while the
 * `Column` it replaced compiles to a `LinearLayout` that simply clips.
 *
 * Composing to `RemoteViews` and inflating them is the cheapest way to see
 * which. The rows themselves are not present in the inflated tree, because a
 * collection's children arrive later from a `RemoteViewsService`; the
 * [AdapterView] that will hold them is, and its presence is the whole assertion.
 */
@RunWith(AndroidJUnit4::class)
class WidgetScrollTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @OptIn(ExperimentalGlanceApi::class)
    @Test
    fun theTaskRowsLiveInAScrollableCollection() = runBlocking {
        val views = FocuslistWidget().compose(context)
        val root = views.apply(context, FrameLayout(context))

        assertNotNull(
            "No AdapterView in the widget: the rows are not scrollable. " +
                describe(root),
            root.firstAdapterView()
        )
    }

    private fun View.firstAdapterView(): AdapterView<*>? {
        if (this is AdapterView<*>) return this
        if (this !is ViewGroup) return null

        for (index in 0 until childCount) {
            getChildAt(index).firstAdapterView()?.let { return it }
        }
        return null
    }

    /** The inflated tree, so a failure says what was there instead. */
    private fun describe(view: View, depth: Int = 0): String {
        val line = "  ".repeat(depth) + view.javaClass.simpleName + "\n"
        if (view !is ViewGroup) return line

        return line + (0 until view.childCount).joinToString("") {
            describe(view.getChildAt(it), depth + 1)
        }
    }
}
