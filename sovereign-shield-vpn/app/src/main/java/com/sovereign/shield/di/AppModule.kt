package com.sovereign.shield.di

import android.content.Context
import com.sovereign.shield.SovereignShieldApp
import com.sovereign.shield.crypto.CryptoManager
import com.sovereign.shield.crypto.EncryptedPrefs
import com.sovereign.shield.network.ApiClient
import com.sovereign.shield.network.VpnApiService
import com.sovereign.shield.vpn.NetworkMonitor
import com.sovereign.shield.vpn.VpnManager
import com.wireguard.android.backend.GoBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGoBackend(@ApplicationContext context: Context): GoBackend {
        return SovereignShieldApp.get(context).backend
    }

    @Provides
    @Singleton
    fun provideEncryptedPrefs(@ApplicationContext context: Context): EncryptedPrefs {
        return EncryptedPrefs(context)
    }

    @Provides
    @Singleton
    fun provideCryptoManager(encryptedPrefs: EncryptedPrefs): CryptoManager {
        return CryptoManager(encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideVpnApiService(): VpnApiService {
        return ApiClient.vpnApi
    }

    @Provides
    @Singleton
    fun provideVpnManager(@ApplicationContext context: Context): VpnManager {
        return VpnManager(context)
    }

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor {
        return NetworkMonitor.getInstance(context)
    }
}
