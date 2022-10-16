package com.yumin.ubike.data


import com.google.gson.annotations.SerializedName

data class StationPosition(
    @SerializedName("GeoHash")
    val geoHash: String,
    @SerializedName("PositionLat")
    val positionLat: Double,
    @SerializedName("PositionLon")
    val positionLon: Double
)