package com.yumin.ubike.repository

import android.util.Log
import com.yumin.ubike.BuildConfig
import com.yumin.ubike.SessionManager
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.StationInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RemoteRepository(private val sessionManager: SessionManager) {
    private val TAG = "[RemoteRepository]"

    suspend fun getAvailabilityByCity(city: String) =
        ApiServiceManager.apiService.getAvailabilityByCity(sessionManager.fetchAuthToken(),city)

    suspend fun getStationInfoByCity(city: String) =
        ApiServiceManager.apiService.getStationInfoByCity(sessionManager.fetchAuthToken(),city)

    suspend fun getStationInfoNearBy(nearBy: String, serviceType: String?) =
        ApiServiceManager.apiService.getStationInfoNearBy(sessionManager.fetchAuthToken(), nearBy, serviceType, "JSON")

    suspend fun getAvailabilityInfoNearBy(nearBy: String, serviceType: String?) =
        ApiServiceManager.apiService.getAvailabilityInfoNearBy(sessionManager.fetchAuthToken(), nearBy, serviceType, "JSON")

    suspend fun getToken() = ApiServiceManager.apiService.getToken(
        "client_credentials", BuildConfig.CLIENT_ID, BuildConfig.CLIENT_SECRET)
}