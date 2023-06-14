package com.yumin.ubike.repository

import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.StationInfo
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @FormUrlEncoded
    @POST("auth/realms/TDXConnect/protocol/openid-connect/token")
    suspend fun getToken(
        @Field("grant_type") type: String,
        @Field("client_id") id: String,
        @Field("client_secret") secret: String
    ): Response<ResponseBody>

    @GET("api/basic/v2/Bike/Station/City/{City}")
    suspend fun getStationInfoByCity(
//        @Header("authorization") token: String? = null,
        @Path("City") city: String
    ): Response<StationInfo>

    @GET("api/basic/v2/Bike/Availability/City/{City}")
    suspend fun getAvailabilityByCity(
//        @Header("authorization") token: String? = null,
        @Path("City") city: String
    ): Response<AvailabilityInfo>

    @GET("api/advanced/v2/Bike/Station/NearBy")
    suspend fun getStationInfoNearBy(
//        @Header("authorization") token: String? = null,
        @Query("\$spatialFilter") nearBy: String,
        @Query("\$filter") serviceType: String? = null,
        @Query("format") format: String
    ): Response<StationInfo>

    @GET("api/advanced/v2/Bike/Availability/NearBy")
    suspend fun getAvailabilityInfoNearBy(
//        @Header("authorization") token: String? = null,
        @Query("\$spatialFilter") nearBy: String,
        @Query("\$filter") serviceType: String? = null,
        @Query("format") format: String
    ): Response<AvailabilityInfo>
}