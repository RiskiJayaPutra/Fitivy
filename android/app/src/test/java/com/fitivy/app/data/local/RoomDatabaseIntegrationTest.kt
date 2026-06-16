package com.fitivy.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitivy.app.data.local.dao.ActivitySessionDao
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class RoomDatabaseIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var sessionDao: ActivitySessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        sessionDao = db.activitySessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeSessionAndReadInList() = runBlocking {
        val session = ActivitySessionEntity(
            id = "test-session-123",
            userId = "user-1",
            startedAt = System.currentTimeMillis(),
            activityType = "running",
            totalSteps = 5000,
            durationSeconds = 1800
        )

        sessionDao.insert(session)

        val activeSession = sessionDao.getActiveSession()
        assertNotNull(activeSession)
        assertEquals("test-session-123", activeSession?.id)
        assertEquals(5000, activeSession?.totalSteps)
    }

    @Test
    fun updateMetricsWorksCorrectly() = runBlocking {
        val session = ActivitySessionEntity(
            id = "test-session-123",
            userId = "user-1",
            startedAt = System.currentTimeMillis()
        )
        sessionDao.insert(session)

        sessionDao.updateMetrics("test-session-123", 1000, 500.0, 50.0, 600, 5.0, 10.0)

        val updated = sessionDao.getById("test-session-123")
        assertEquals(1000, updated?.totalSteps)
        assertEquals(500.0, updated?.distanceMeters)
        assertEquals(600L, updated?.durationSeconds)
    }
}
