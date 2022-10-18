package com.yumin.ubike.repository

import android.util.Log
import com.yumin.ubike.data.StationInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RemoteRepository {
    private var remoteApiService: ApiService = ApiServiceManager.apiService

    suspend fun getStationInfoByCity(city:String): StationInfo {
        return suspendCancellableCoroutine {
            remoteApiService.getStationInfoByCity(city).enqueue(
                object : Callback<StationInfo> {
                    override fun onResponse(
                        call: Call<StationInfo>,
                        response: Response<StationInfo>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<StationInfo>)
                        Log.d("Repository", "onResponse list = "+response.isSuccessful)
                    }

                    override fun onFailure(call: Call<StationInfo>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                        Log.d("Repository", "onFailure")
                    }
                }
            )
        }
    }
}