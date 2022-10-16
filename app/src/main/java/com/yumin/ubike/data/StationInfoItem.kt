package com.yumin.ubike.data


import com.google.gson.annotations.SerializedName

data class StationInfoItem(
    @SerializedName("AuthorityID")
    val authorityID: String,
    @SerializedName("BikesCapacity")
    val bikesCapacity: Int,
    @SerializedName("ServiceType")
    val serviceType: Int,
    @SerializedName("SrcUpdateTime")
    val srcUpdateTime: String,
    @SerializedName("StationAddress")
    val stationAddress: StationAddress,
    @SerializedName("StationID")
    val stationID: String,
    @SerializedName("StationName")
    val stationName: StationName,
    @SerializedName("StationPosition")
    val stationPosition: StationPosition,
    @SerializedName("StationUID")
    val stationUID: String,
    @SerializedName("UpdateTime")
    val updateTime: String
)