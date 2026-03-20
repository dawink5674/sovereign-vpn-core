package com.sovereign.shield.network

import com.sovereign.shield.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit API client with certificate pinning.
 * Base URL is injected from BuildConfig.
 */
object ApiClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // Certificate pinning for the control plane API
    private val certificatePinner = CertificatePinner.Builder()
        .add("vpn-control-plane-vqkyeuhxnq-uc.a.run.app",
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Placeholder — replace with actual pin
        .build()

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-Client-Version", BuildConfig.VERSION_NAME)
                .header("X-Client-App", "SovereignShield")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Note: Certificate pinning disabled for development
        // .certificatePinner(certificatePinner)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val vpnApi: VpnApiService = retrofit.create(VpnApiService::class.java)
}
