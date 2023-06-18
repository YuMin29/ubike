package com.yumin.ubike

import android.location.Location
import android.location.LocationManager
import com.yumin.ubike.data.StationInfoItem
import java.util.*
import kotlin.Comparator

object SortUtils {
    fun sortListByDistance(stationList: ArrayList<StationInfoItem>): ArrayList<StationInfoItem> {
        val comparator = Comparator<StationInfoItem?> { item1, item2 ->
            var distance1 = 0f
            item1?.let {
                val location1 = Location(LocationManager.NETWORK_PROVIDER).apply {
                    latitude = item1.stationPosition.positionLat
                    longitude = item1.stationPosition.positionLon
                }

                distance1 = MapFragment.currentLocation.distanceTo(location1)
            }

            var distance2 = 0f
            item2?.let {
                val location2 = Location(LocationManager.NETWORK_PROVIDER).apply {
                    latitude = item2.stationPosition.positionLat
                    longitude = item2.stationPosition.positionLon
                }
                distance2 = MapFragment.currentLocation.distanceTo(location2)
            }
//            Log.d(TAG, "distance1 : $distance1, distance2 : $distance2")
//            Log.d(TAG, "o1 : ${item1?.stationName}, o2 : ${item2?.stationName}")
            distance1.compareTo(distance2)
        }
        Collections.sort(stationList, comparator)
        return stationList
    }
}