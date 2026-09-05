package com.vignesh.focuslist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.data.local.FocuslistDatabase
import com.vignesh.focuslist.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Verifies the production composition root and that a real on-disk database
 * actually persists.
 *
 * The round-trip test uses its own file rather than [FocuslistApplication.DATABASE_NAME],
 * so running the suite never touches real data on the device. It still exercises
 * a genuine persistent database, which is the point.
 */
@RunWith(AndroidJUnit4::class)
class FocuslistApplicationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")
    private val scheduled: LocalDate = LocalDate.of(2026, 8, 31)

    @Before
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    private fun openTestDatabase(): FocuslistDatabase =
        Room.databaseBuilder(context, FocuslistDatabase::class.java, TEST_DATABASE_NAME).build()

    @Test
    fun theApplicationConstructsTheDatabaseAndRepository() {
        val application = context as FocuslistApplication

        assertNotNull(application.database)
        assertNotNull(application.taskRepository)
        assertNotNull(application.database.taskDao())
    }

    @Test
    fun theApplicationReusesOneDatabaseAndOneRepository() {
        val application = context as FocuslistApplication

        // A new instance per access would mean a new connection pool on every
        // configuration change.
        assertSame(application.database, application.database)
        assertSame(application.taskRepository, application.taskRepository)
    }

    @Test
    fun theApplicationRepositoryIsUsable() = runBlocking {
        val application = context as FocuslistApplication

        // Reading through the real production database must not throw.
        assertNotNull(application.taskRepository.observeTasks().first())
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val task = Task(
            id = "persisted",
            title = "Survive a restart",
            createdAt = createdAt,
            scheduledDate = scheduled,
            estimatedDurationMinutes = 45
        )

        val first = openTestDatabase()
        TaskRepository(first.taskDao()).insert(task)
        first.close()

        val second = openTestDatabase()
        try {
            val reloaded = TaskRepository(second.taskDao()).observeTasks().first()

            assertEquals(listOf(task), reloaded)
        } finally {
            second.close()
        }
    }

    @Test
    fun theDatabaseFileIsCreatedOnDisk() = runBlocking {
        val database = openTestDatabase()
        try {
            TaskRepository(database.taskDao()).insert(
                Task(id = "a", title = "Task a", createdAt = createdAt)
            )
        } finally {
            database.close()
        }

        // An in-memory database would leave nothing behind.
        assertEquals(true, context.databaseList().contains(TEST_DATABASE_NAME))
    }

    private companion object {
        const val TEST_DATABASE_NAME = "focuslist-persistence-test.db"
    }
}
