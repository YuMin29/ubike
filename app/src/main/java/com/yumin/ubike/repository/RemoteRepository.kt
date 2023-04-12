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
    private var remoteApiService: ApiService = ApiServiceManager.apiService

    companion object{
        private const val TAG = "[RemoteRepository]"
    }

    suspend fun getAvailabilityByCity(city: String): AvailabilityInfo {
        return suspendCancellableCoroutine {
            remoteApiService.getAvailabilityByCity(sessionManager.fetchAuthToken(), city).enqueue(
                object : Callback<AvailabilityInfo> {
                    override fun onResponse(
                        call: Call<AvailabilityInfo>,
                        response: Response<AvailabilityInfo>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<AvailabilityInfo>)
                        Log.d(TAG, "getAvailabilityByCity isSuccessful : " + response.isSuccessful)
                    }

                    override fun onFailure(call: Call<AvailabilityInfo>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                        Log.d(TAG, "getAvailabilityByCity onFailure")
                    }
                }
            )
        }
    }

    suspend fun getStationInfoByCity(city: String): StationInfo {
        return suspendCancellableCoroutine {
            remoteApiService.getStationInfoByCity(sessionManager.fetchAuthToken(), city).enqueue(
                object : Callback<StationInfo> {
                    override fun onResponse(
                        call: Call<StationInfo>,
                        response: Response<StationInfo>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<StationInfo>)
                        Log.d(TAG, "getStationInfoByCity isSuccessful = " + response.isSuccessful)
                    }

                    override fun onFailure(call: Call<StationInfo>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                        Log.d(TAG, "getStationInfoByCity onFailure")
                    }
                }
            )
        }
    }

    suspend fun getStationInfoNearBy(nearBy: String, serviceType: String?): StationInfo {
        Log.d(TAG, "[getStationInfoNearBy] nearBy = $nearBy")
        return suspendCancellableCoroutine {
            remoteApiService.getStationInfoNearBy(
                sessionManager.fetchAuthToken(),
                nearBy,
                serviceType,
                "JSON"
            ).enqueue(
                object : Callback<StationInfo> {
                    override fun onResponse(
                        call: Call<StationInfo>,
                        response: Response<StationInfo>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<StationInfo>)
                        Log.d(TAG, "[getStationInfoNearBy] onResponse : ${response.body()}")
                    }

                    override fun onFailure(call: Call<StationInfo>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                        Log.d(TAG, "[getStationInfoNearBy] onFailure : " + t.message)
                    }
                }
            )
        }
    }

    suspend fun getAvailabilityInfoNearBy(nearBy: String, serviceType: String?): AvailabilityInfo {
        return suspendCancellableCoroutine {
            remoteApiService.getAvailabilityInfoNearBy(
                sessionManager.fetchAuthToken(),
                nearBy,
                serviceType,
                "JSON"
            ).enqueue(
                object : Callback<AvailabilityInfo> {
                    override fun onResponse(
                        call: Call<AvailabilityInfo>,
                        response: Response<AvailabilityInfo>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<AvailabilityInfo>)
                        Log.d(TAG, "[getAvailabilityInfoNearBy] onResponse : ${response.body()}")
                    }

                    override fun onFailure(call: Call<AvailabilityInfo>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                        Log.d(TAG, "[getAvailabilityInfoNearBy] onFailure : " + t.message)
                    }
                }
            )
        }
    }

    suspend fun getToken() {
        // suspendCancellableCoroutine -> 具有回傳值，且可以返回exception
        return suspendCancellableCoroutine {
            remoteApiService.getToken(
                "client_credentials",
                BuildConfig.CLIENT_ID, BuildConfig.CLIENT_SECRET
            ).enqueue(                              //enqueue->發出請求
                object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {

                        var bodyContent = response.body()?.string()
                        var jsonObject = JSONObject(bodyContent)
                        Log.d(
                            "RemoteRepository",
                            "getToken onResponse access_token = " + jsonObject.get("access_token")
                        )
                        val token = jsonObject.get("access_token").toString()
//                        ApiServiceManager.tokenFromServer = String.format("Bearer %s", token)
                        sessionManager.saveAuthToken(String.format("Bearer %s", token))
//                        it.resumeWith(Result.success(String.format("Bearer %s", token)))
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("RemoteRepository", "getToken onFailure = " + t.message)
                    }
                }
            )
        }
    }
}