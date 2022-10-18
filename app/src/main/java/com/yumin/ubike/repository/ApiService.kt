package com.yumin.ubike.repository

import com.yumin.ubike.data.StationInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/basic/v2/Bike/Station/{City}?%24top=30&%24format=JSON")
    fun getStationInfoByCity(@Path("City")city:String): Call<StationInfo>
}