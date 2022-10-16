package com.yumin.ubike.repository

import retrofit2.http.GET

interface ApiService {
    @GET("")
    fun getStationInfoByCity(){}
}