package com.pocketssh.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.pocketssh.app.data.db.AppDatabase
import com.pocketssh.app.data.db.ConnectionProfileDao
import com.pocketssh.app.data.repository.ConnectionRepository
import com.pocketssh.app.data.repository.ConnectionRepositoryImpl
import com.pocketssh.app.security.BiometricHelper
import com.pocketssh.app.security.BiometricHelperImpl
import com.pocketssh.app.security.KeyStorageManager
import com.pocketssh.app.security.KeyStorageManagerImpl
import com.pocketssh.app.security.SshKeyManagerAdapter
import com.pocketssh.app.session.TmuxManager
import com.pocketssh.app.session.TmuxManagerImpl
import com.pocketssh.app.ssh.SshKeyManager
import com.pocketssh.app.ssh.SshManager
import com.pocketssh.app.ssh.SshManagerImpl
import com.pocketssh.app.terminal.TerminalSessionManager
import com.pocketssh.app.terminal.TerminalSessionManagerImpl
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
