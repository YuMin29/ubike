package com.yumin.ubike.repository

import com.yumin.ubike.BuildConfig
import com.yumin.ubike.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

class UbikeRepository @Inject constructor(private val apiService: ApiService){
    suspend fun getAvailabilityByCity(city: String) =
        apiService.getAvailabilityByCity(city)

    suspend fun getStationInfoByCity(city: String) =
        apiService.getStationInfoByCity(city)

    suspend fun getStationInfoNearBy(nearBy: String, serviceType: String?) =
        apiService.getStationInfoNearBy(nearBy, serviceType, "JSON")

    suspend fun getAvailabilityInfoNearBy(nearBy: String, serviceType: String?) =
        apiService.getAvailabilityInfoNearBy(nearBy, serviceType, "JSON")

    suspend fun getToken() = apiService.getToken(
        "client_credentials", BuildConfig.CLIENT_ID, BuildConfig.CLIENT_SECRET)
}