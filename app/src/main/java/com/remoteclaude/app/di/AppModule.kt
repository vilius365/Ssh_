package com.remoteclaude.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.remoteclaude.app.data.db.AppDatabase
import com.remoteclaude.app.data.db.ConnectionProfileDao
import com.remoteclaude.app.data.repository.ConnectionRepository
import com.remoteclaude.app.data.repository.ConnectionRepositoryImpl
import com.remoteclaude.app.security.BiometricHelper
import com.remoteclaude.app.security.BiometricHelperImpl
import com.remoteclaude.app.security.KeyStorageManager
import com.remoteclaude.app.security.KeyStorageManagerImpl
import com.remoteclaude.app.security.SshKeyManagerAdapter
import com.remoteclaude.app.session.TmuxManager
import com.remoteclaude.app.session.TmuxManagerImpl
import com.remoteclaude.app.ssh.SshKeyManager
import com.remoteclaude.app.ssh.SshManager
import com.remoteclaude.app.ssh.SshManagerImpl
import com.remoteclaude.app.terminal.TerminalSessionManager
import com.remoteclaude.app.terminal.TerminalSessionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "claude_terminal_settings"
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "claude_terminal.db",
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideConnectionProfileDao(database: AppDatabase): ConnectionProfileDao =
        database.connectionProfileDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: ConnectionRepositoryImpl): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindSshManager(impl: SshManagerImpl): SshManager

    @Binds
    @Singleton
    abstract fun bindTmuxManager(impl: TmuxManagerImpl): TmuxManager

    @Binds
    @Singleton
    abstract fun bindKeyStorageManager(impl: KeyStorageManagerImpl): KeyStorageManager

    @Binds
    @Singleton
    abstract fun bindSshKeyManager(impl: SshKeyManagerAdapter): SshKeyManager

    @Binds
    @Singleton
    abstract fun bindBiometricHelper(impl: BiometricHelperImpl): BiometricHelper

    @Binds
    @Singleton
    abstract fun bindTerminalSessionManager(impl: TerminalSessionManagerImpl): TerminalSessionManager
}
