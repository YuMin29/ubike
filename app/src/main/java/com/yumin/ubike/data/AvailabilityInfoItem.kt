package com.yumin.ubike.data

data class AvailabilityInfoItem(
    val AvailableRentBikes: Int,
    val AvailableRentBikesDetail: AvailableRentBikesDetail,
    val AvailableReturnBikes: Int,
    val ServiceStatus: Int,
    val ServiceType: Int,
    val SrcUpdateTime: String,
    val StationID: String,
    val StationUID: String,
    val UpdateTime: String
)