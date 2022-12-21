package com.yumin.ubike.repository

import android.util.Log
import com.yumin.ubike.BuildConfig
import com.yumin.ubike.data.StationInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RemoteRepository {
    private var remoteApiService: ApiService = ApiServiceManager.apiService

    suspend fun getStationInfoByCity(token: String, city: String): StationInfo {
        return suspendCancellableCoroutine {
            remoteApiService.getStationInfoByCity(token, city).enqueue(
                object : Callback<StationInfo> {
                    override fun onResponse(
                        call: Call<StationInfo>,
                        response: Response<StationInfo>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<StationInfo>)
                        Log.d(
                            "RemoteRepository",
                            "getStationInfoByCity list = " + response.isSuccessful
                        )
                    }

                    override fun onFailure(call: Call<StationInfo>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                        Log.d("RemoteRepository", "getStationInfoByCity onFailure")
                    }
                }
            )
        }
    }

    suspend fun getToken(): String {
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
                        ApiServiceManager.tokenFromServer = String.format("Bearer %s", token)
                        it.resumeWith(Result.success(String.format("Bearer %s", token)))
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        Log.d("RemoteRepository", "getToken onFailure = " + t.message)
                    }
                }
            )
        }
    }
}