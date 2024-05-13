package com.yumin.ubike.data

data class UbikeStationInfoItem(
    val availableRentBikes: Int,
    val availableRentBikesDetail: AvailableRentBikesDetail,
    val availableReturnBikes: Int,
    val serviceStatus: Int,
    val serviceType: Int,
    val stationID: String,
    val authorityID: String,
    val bikesCapacity: Int,
    val srcUpdateTime: String,
    val stationAddress: StationAddress,
    val stationName: StationName,
    val stationPosition: StationPosition,
    val stationUID: String,
    val updateTime: String
)

data class UbikeStationWithFavorite(val item: UbikeStationInfoItem, val isFavorite: Boolean)