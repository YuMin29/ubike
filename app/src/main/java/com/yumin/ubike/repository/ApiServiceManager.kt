package com.yumin.ubike.repository

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiServiceManager {
    private const val BASE_URL = "https://tdx.transportdata.tw/"
    private var httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        this.level = HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10000, TimeUnit.MILLISECONDS)
            .addInterceptor(httpLoggingInterceptor)
            .build()

    private val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}