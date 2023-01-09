package com.yumin.ubike.repository

import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.StationInfo
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @GET("api/basic/v2/Bike/Station/City/{City}")
    fun getStationInfoByCity(
        @Header("authorization") token: String,
        @Path("City") city: String
    ): Call<StationInfo>

    @FormUrlEncoded
    @POST("auth/realms/TDXConnect/protocol/openid-connect/token")
    fun getToken(
        @Field("grant_type") type: String,
        @Field("client_id") id: String,
        @Field("client_secret") secret: String
    ): Call<ResponseBody>

    @GET("api/basic/v2/Bike/Availability/City/{City}")
    fun getAvailabilityByCity(
        @Header("authorization") token: String,
        @Path("City") city: String
    ): Call<AvailabilityInfo>
}