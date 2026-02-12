package com.remoteclaude.app.data.repository

import com.remoteclaude.app.data.db.ConnectionProfileDao
import com.remoteclaude.app.data.db.ConnectionProfileEntity
import com.remoteclaude.app.data.model.ConnectionProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionRepositoryImplTest {

    private lateinit var dao: ConnectionProfileDao
    private lateinit var repository: ConnectionRepositoryImpl

    private val sampleEntity = ConnectionProfileEntity(
        id = 1L,
        nickname = "Dev Server",
        hostname = "dev.example.com",
        port = 22,
        username = "admin",
        sshKeyId = "key-123",
        lastConnectedAt = 1707650000L,
        defaultTmuxSession = "main",
        isDefault = true,
        createdAt = 1707600000L,
        updatedAt = 1707640000L,
    )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = ConnectionRepositoryImpl(dao)
    }

    // ---------------------------------------------------------------
    // Entity <-> Domain mapping
    // ---------------------------------------------------------------

    @Test
    fun `getById maps entity to domain model correctly`() = runTest {
        coEvery { dao.getById(1L) } returns sampleEntity

        val profile = repository.getById(1L)

        assertNotNull(profile)
        profile!!
        assertEquals(1L, profile.id)
        assertEquals("Dev Server", profile.nickname)
        assertEquals("dev.example.com", profile.hostname)
        assertEquals(22, profile.port)
        assertEquals("admin", profile.username)
        assertEquals("key-123", profile.sshKeyId)
        assertEquals(1707650000L, profile.lastConnectedAt)
        assertEquals("main", profile.defaultTmuxSession)
        assertTrue(profile.isDefault)
        assertEquals(1707600000L, profile.createdAt)
        assertEquals(1707640000L, profile.updatedAt)
    }

    @Test
    fun `getById returns null when entity not found`() = runTest {
        coEvery { dao.getById(99L) } returns null

        assertNull(repository.getById(99L))
    }

    @Test
    fun `save maps domain model to entity correctly`() = runTest {
        val entitySlot = slot<ConnectionProfileEntity>()
        coEvery { dao.upsert(capture(entitySlot)) } returns 5L

        val profile = ConnectionProfile(
            id = 5L,
            nickname = "Prod",
            hostname = "prod.example.com",
            port = 2222,
            username = "deploy",
            sshKeyId = "key-456",
            lastConnectedAt = 1707660000L,
            defaultTmuxSession = "deploy",
            isDefault = false,
            createdAt = 1707600000L,
            updatedAt = 1707640000L,
        )

        repository.save(profile)

        val captured = entitySlot.captured
        assertEquals(5L, captured.id)
        assertEquals("Prod", captured.nickname)
        assertEquals("prod.example.com", captured.hostname)
        assertEquals(2222, captured.port)
        assertEquals("deploy", captured.username)
        assertEquals("key-456", captured.sshKeyId)
        assertEquals(1707660000L, captured.lastConnectedAt)
        assertEquals("deploy", captured.defaultTmuxSession)
        assertFalse(captured.isDefault)
    }

    @Test
    fun `save sets createdAt and updatedAt for new profile`() = runTest {
        val entitySlot = slot<ConnectionProfileEntity>()
        coEvery { dao.upsert(capture(entitySlot)) } returns 1L

        val profile = ConnectionProfile(
            id = 0L,
            nickname = "New",
            hostname = "new.example.com",
            username = "user",
        )

        val beforeMs = System.currentTimeMillis()
        repository.save(profile)
        val afterMs = System.currentTimeMillis()

        val captured = entitySlot.captured
        assertTrue(captured.createdAt in beforeMs..afterMs)
        assertTrue(captured.updatedAt in beforeMs..afterMs)
    }

    @Test
    fun `save updates updatedAt for existing profile`() = runTest {
        val entitySlot = slot<ConnectionProfileEntity>()
        coEvery { dao.upsert(capture(entitySlot)) } returns 5L

        val profile = ConnectionProfile(
            id = 5L,
            nickname = "Existing",
            hostname = "existing.example.com",
            username = "user",
            createdAt = 1707600000L,
        )

        val beforeMs = System.currentTimeMillis()
        repository.save(profile)
        val afterMs = System.currentTimeMillis()

        val captured = entitySlot.captured
        // updatedAt should be refreshed
        assertTrue(captured.updatedAt in beforeMs..afterMs)
    }

    // ---------------------------------------------------------------
    // observeAll
    // ---------------------------------------------------------------

    @Test
    fun `observeAll maps all entities to domain models`() = runTest {
        val entities = listOf(
            sampleEntity,
            sampleEntity.copy(id = 2L, nickname = "Staging", hostname = "staging.example.com"),
        )
        coEvery { dao.observeAll() } returns flowOf(entities)

        val profiles = repository.observeAll().first()

        assertEquals(2, profiles.size)
        assertEquals("Dev Server", profiles[0].nickname)
        assertEquals("Staging", profiles[1].nickname)
    }

    @Test
    fun `observeAll returns empty list when no entities`() = runTest {
        coEvery { dao.observeAll() } returns flowOf(emptyList())

        val profiles = repository.observeAll().first()

        assertTrue(profiles.isEmpty())
    }

    // ---------------------------------------------------------------
    // setDefault (Fix I5: now uses transactional DAO method)
    // ---------------------------------------------------------------

    @Test
    fun `setDefault delegates to transactional DAO method`() = runTest {
        repository.setDefault(1L)

        coVerify { dao.setDefaultTransactional(1L) }
    }

    // ---------------------------------------------------------------
    // updateLastConnected
    // ---------------------------------------------------------------

    @Test
    fun `updateLastConnected delegates to dao with current timestamp`() = runTest {
        val timestampSlot = slot<Long>()
        coEvery { dao.updateLastConnected(1L, capture(timestampSlot)) } returns Unit

        val beforeMs = System.currentTimeMillis()
        repository.updateLastConnected(1L)
        val afterMs = System.currentTimeMillis()

        coVerify { dao.updateLastConnected(1L, any()) }
        assertTrue(timestampSlot.captured in beforeMs..afterMs)
    }

    // ---------------------------------------------------------------
    // getDefault / getMostRecent
    // ---------------------------------------------------------------

    @Test
    fun `getDefault maps entity to domain model`() = runTest {
        coEvery { dao.getDefault() } returns sampleEntity

        val profile = repository.getDefault()

        assertNotNull(profile)
        assertEquals("Dev Server", profile!!.nickname)
    }

    @Test
    fun `getDefault returns null when none set`() = runTest {
        coEvery { dao.getDefault() } returns null

        assertNull(repository.getDefault())
    }

    @Test
    fun `getMostRecent returns mapped profile`() = runTest {
        coEvery { dao.getMostRecent() } returns sampleEntity

        val profile = repository.getMostRecent()

        assertNotNull(profile)
        assertEquals("Dev Server", profile!!.nickname)
    }

    // ---------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------

    @Test
    fun `delete calls dao with mapped entity`() = runTest {
        val entitySlot = slot<ConnectionProfileEntity>()
        coEvery { dao.delete(capture(entitySlot)) } returns Unit

        val profile = ConnectionProfile(
            id = 1L,
            nickname = "ToDelete",
            hostname = "delete.example.com",
            username = "user",
        )

        repository.delete(profile)

        assertEquals(1L, entitySlot.captured.id)
        assertEquals("ToDelete", entitySlot.captured.nickname)
    }
}
