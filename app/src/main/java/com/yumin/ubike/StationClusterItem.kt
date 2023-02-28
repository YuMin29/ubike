package com.yumin.ubike

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem

// TODO 改DATA CLASS?
class StationClusterItem() : ClusterItem {
    private lateinit var position: LatLng
    private lateinit var title: String
    private lateinit var snippet: String
    private val zIndex: Float? = null
    var imageId: Int = 0
    private lateinit var stationInfoItem: StationInfoItem
    private lateinit var stationUid: String
    lateinit var availabilityInfoItem: AvailabilityInfoItem

    constructor(
        latitude: Double,
        longitude: Double,
        title: String,
        snippet: String,
        imageId: Int,
        stationInfoItem: StationInfoItem,
        stationUid: String,
        availabilityInfoItem: AvailabilityInfoItem
    ) : this() {
        this.position = LatLng(latitude, longitude)
        this.title = title
        this.snippet = snippet
        this.imageId = imageId
        this.stationInfoItem = stationInfoItem
        this.stationUid = stationUid
        this.availabilityInfoItem = availabilityInfoItem
    }

    override fun getPosition(): LatLng {
        return position
    }

    override fun getTitle(): String? {
        return title
    }

    override fun getSnippet(): String? {
        return snippet
    }

    override fun getZIndex(): Float? {
        return zIndex
    }

    fun getStationInfoItem(): StationInfoItem {
        return stationInfoItem
    }

    fun getStationUid(): String {
        return stationUid
    }

    fun getAvailableInfoItem(): AvailabilityInfoItem {
        return availabilityInfoItem
    }
}