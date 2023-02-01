package com.yumin.ubike.repository

import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.StationInfo
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @FormUrlEncoded
    @POST("auth/realms/TDXConnect/protocol/openid-connect/token")
    fun getToken(
        @Field("grant_type") type: String,
        @Field("client_id") id: String,
        @Field("client_secret") secret: String
    ): Call<ResponseBody>

    @GET("api/basic/v2/Bike/Station/City/{City}")
    fun getStationInfoByCity(
        @Header("authorization") token: String,
        @Path("City") city: String
    ): Call<StationInfo>

    @GET("api/basic/v2/Bike/Availability/City/{City}")
    fun getAvailabilityByCity(
        @Header("authorization") token: String,
        @Path("City") city: String
    ): Call<AvailabilityInfo>

    @GET("api/advanced/v2/Bike/Station/NearBy")
    fun getStationInfoNearBy(
        @Header("authorization") token: String,
        @Query("\$spatialFilter") nearBy: String,
        @Query("format") format: String
    ): Call<StationInfo>

    @GET("api/advanced/v2/Bike/Availability/NearBy")
    fun getAvailabilityInfoBearBy(
        @Header("authorization") token: String,
        @Query("\$spatialFilter") nearBy: String,
        @Query("format") format: String
    ): Call<AvailabilityInfo>
}