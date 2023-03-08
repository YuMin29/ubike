package com.yumin.ubike

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem

data class StationClusterItem(
    val itemPosition: LatLng,
    val itemTitle: String,
    val itemSnippet: String,
    val itemZIndex: Float? = null,
    var imageId: Int = 0,
    val stationInfoItem: StationInfoItem,
    val stationUid: String,
    var availabilityInfoItem: AvailabilityInfoItem
) : ClusterItem {
    override fun getPosition() = itemPosition
    override fun getSnippet() = itemSnippet
    override fun getTitle() = itemTitle
    override fun getZIndex() = itemZIndex
}