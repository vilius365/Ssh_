package com.remoteclaude.app.data.repository

import com.remoteclaude.app.data.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing connection profiles.
 */
interface ConnectionRepository {

    /** Observe all connection profiles, ordered by most recently connected. */
    fun observeAll(): Flow<List<ConnectionProfile>>

    /** Get a single connection profile by ID. */
    suspend fun getById(id: Long): ConnectionProfile?

    /** Get the profile marked as default, or null if none is set. */
    suspend fun getDefault(): ConnectionProfile?

    /** Get the most recently connected profile. */
    suspend fun getMostRecent(): ConnectionProfile?

    /** Save a new or updated connection profile. Returns the profile ID. */
    suspend fun save(profile: ConnectionProfile): Long

    /** Delete a connection profile. */
    suspend fun delete(profile: ConnectionProfile)

    /** Mark a profile as the default, clearing any previous default. */
    suspend fun setDefault(id: Long)

    /** Update the last connected timestamp for a profile. */
    suspend fun updateLastConnected(id: Long)
}
