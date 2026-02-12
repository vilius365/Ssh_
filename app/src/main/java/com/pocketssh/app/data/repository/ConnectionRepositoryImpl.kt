package com.pocketssh.app.data.repository

import com.pocketssh.app.data.db.ConnectionProfileDao
import com.pocketssh.app.data.db.ConnectionProfileEntity
import com.pocketssh.app.data.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val dao: ConnectionProfileDao,
) : ConnectionRepository {

    override fun observeAll(): Flow<List<ConnectionProfile>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: Long): ConnectionProfile? =
        dao.getById(id)?.toDomain()

    override suspend fun getDefault(): ConnectionProfile? =
        dao.getDefault()?.toDomain()

    override suspend fun getMostRecent(): ConnectionProfile? =
        dao.getMostRecent()?.toDomain()

    override suspend fun save(profile: ConnectionProfile): Long {
        val now = System.currentTimeMillis()
        return if (profile.id == 0L) {
            dao.upsert(profile.toEntity().copy(createdAt = now, updatedAt = now))
        } else {
            dao.upsert(profile.toEntity().copy(updatedAt = now))
        }
    }

    override suspend fun delete(profile: ConnectionProfile) {
        dao.delete(profile.toEntity())
    }

    // Fix I5: Use transactional method to prevent race between clear and set
    override suspend fun setDefault(id: Long) {
        dao.setDefaultTransactional(id)
    }

    override suspend fun updateLastConnected(id: Long) {
        dao.updateLastConnected(id, System.currentTimeMillis())
    }
}

private fun ConnectionProfileEntity.toDomain() = ConnectionProfile(
    id = id,
    nickname = nickname,
    hostname = hostname,
    port = port,
    username = username,
    sshKeyId = sshKeyId,
    lastConnectedAt = lastConnectedAt,
    defaultTmuxSession = defaultTmuxSession,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ConnectionProfile.toEntity() = ConnectionProfileEntity(
    id = id,
    nickname = nickname,
    hostname = hostname,
    port = port,
    username = username,
    sshKeyId = sshKeyId,
    lastConnectedAt = lastConnectedAt,
    defaultTmuxSession = defaultTmuxSession,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
