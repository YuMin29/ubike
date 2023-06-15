package com.yumin.ubike.di

import android.content.Context
import com.yumin.ubike.SessionManager
import com.yumin.ubike.repository.ApiService
import com.yumin.ubike.repository.UbikeRepository
import com.yumin.ubike.repository.TokenAuthInterceptor
import dagger.Lazy
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Module {

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideRemoteRepository(retrofit: Retrofit): UbikeRepository {
        return UbikeRepository(retrofit.create(ApiService::class.java))
    }

    @Provides
    @Singleton
    fun provideTokenAuthInterceptor(sessionManager: SessionManager, repository: Lazy<UbikeRepository>, @ApplicationContext context: Context): TokenAuthInterceptor {
        return TokenAuthInterceptor(sessionManager, repository, context)
    }

    @Provides
    @Singleton
    fun provideRetrofit(tokenAuthInterceptor: TokenAuthInterceptor): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://tdx.transportdata.tw/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(5000, TimeUnit.MILLISECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        this.level = HttpLoggingInterceptor.Level.NONE
                    })
                    .addInterceptor(tokenAuthInterceptor)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }
}