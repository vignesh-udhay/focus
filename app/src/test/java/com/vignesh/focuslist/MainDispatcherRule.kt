package com.vignesh.focuslist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.Executors

/**
 * Gives `Dispatchers.Main` an implementation for the length of the test JVM.
 *
 * A view model observes through `viewModelScope`, which dispatches on the main
 * dispatcher. On the JVM that dispatcher is absent, so without this rule the
 * first collection throws and nothing a view model exposes can be tested off a
 * device. That absence, and nothing Android in the code under test, is what
 * used to send those tests through an emulator.
 *
 * A real single thread, not one of the test dispatchers, and installed once for
 * the whole class rather than around each test. Both were found the hard way.
 *
 * `UnconfinedTestDispatcher` runs eagerly on whichever thread resumed the
 * coroutine, which here is the test's own. A test that acts and then waits by
 * polling blocks that thread, so the operators built on channels —
 * `flatMapLatest` and friends — never drain, and the session clock silently
 * stopped restarting. `StandardTestDispatcher` needs an explicit hand-off the
 * polling helpers do not make. A single real thread is what Android's main
 * dispatcher is: work is serialised and makes progress while the test waits,
 * which is how these tests were written. `Dispatchers.Default` would run that
 * work on several threads at once and could produce interleavings the real
 * main dispatcher never does.
 *
 * Installed per class because nothing ever clears the view models a test
 * builds, so their collectors are still live when the next test starts.
 * Swapping the dispatcher underneath them throws "Dispatchers.Main is used
 * concurrently with setting it", which is true and is the test's doing, not the
 * code's. A process has one main dispatcher for its whole life; so does this.
 *
 * Never reset, for the same reason: a reset is another swap, and the coroutines
 * that would object are still running when the last test ends. The thread is a
 * daemon, so it cannot hold the JVM open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "test-main").apply { isDaemon = true }
            }.asCoroutineDispatcher()
        )
    }
}
