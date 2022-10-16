package com.yumin.ubike.repository

import com.yumin.ubike.data.StationInfo
import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("Station/")
    fun getStationInfoByCity(city:String): Call<List<StationInfo>>
}