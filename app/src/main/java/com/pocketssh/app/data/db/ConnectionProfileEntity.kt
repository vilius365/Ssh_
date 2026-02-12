package com.pocketssh.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "connection_profiles",
    indices = [
        Index(value = ["is_default"]),
        Index(value = ["last_connected_at"]),
    ],
)
data class ConnectionProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nickname: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    @ColumnInfo(name = "ssh_key_id")
    val sshKeyId: String? = null,
    @ColumnInfo(name = "last_connected_at")
    val lastConnectedAt: Long? = null,
    @ColumnInfo(name = "default_tmux_session")
    val defaultTmuxSession: String? = null,
    @ColumnInfo(name = "is_default", defaultValue = "0")
    val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
