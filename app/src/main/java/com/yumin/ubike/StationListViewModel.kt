package com.yumin.ubike

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.repository.RemoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StationListViewModel(private val repository: RemoteRepository) : ViewModel() {
    var stationInfo = MutableLiveData<StationInfo>()

    fun getStationInfoNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(
                "StationListViewModel",
                "[getStationInfoNearBy] longitude = $latitude ,latitude = $longitude" +
                        ",distance = $distance , type = $type"
            )

            var queryServiceType: String? = when (type) {
                1 -> "ServiceType eq '1'"
                2 -> "ServiceType eq '2'"
                else -> null
            }

            stationInfo.postValue(
                repository.getStationInfoNearBy(
                    "nearby($latitude, $longitude, $distance)",
                    queryServiceType
                )
            )
        }
    }

}