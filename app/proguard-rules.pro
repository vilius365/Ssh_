# ConnectBot sshlib
-keep class com.trilead.ssh2.** { *; }
-dontwarn com.trilead.ssh2.**

# Termux terminal-emulator
-keep class com.termux.terminal.** { *; }
-dontwarn com.termux.terminal.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Google Tink (transitive dependency via ConnectBot sshlib)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
