package com.yumin.ubike.repository

import com.yumin.ubike.data.StationInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RemoteRepository {
    private var remoteApiService: ApiService = ApiServiceManager.apiService

    suspend fun getStationInfoByCity(city:String): List<StationInfo> {
        return suspendCancellableCoroutine {
            remoteApiService.getStationInfoByCity(city).enqueue(
                object : Callback<List<StationInfo>> {
                    override fun onResponse(
                        call: Call<List<StationInfo>>,
                        response: Response<List<StationInfo>>
                    ) {
                        it.resumeWith(Result.success(response.body()) as Result<List<StationInfo>>)
                    }

                    override fun onFailure(call: Call<List<StationInfo>>, t: Throwable) {
                        it.resumeWith(Result.failure(t))
                    }
                }
            )
        }
    }
}