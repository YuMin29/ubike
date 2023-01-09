package com.yumin.ubike

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.repository.RemoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapViewModel(private val repository: RemoteRepository, private val context: Context) :
    ViewModel() {
    lateinit var token: String
    var stationInfoList = MutableLiveData<StationInfo>()
    var availabilityInfoList = MutableLiveData<AvailabilityInfo>()

//    init {
//        loadStationInfo("NewTaipei")
//    }

    fun loadStationInfo(city: String) {
        viewModelScope.launch(Dispatchers.IO) {
            token = repository.getToken()
            stationInfoList.postValue(repository.getStationInfoByCity(token, city))
        }
    }

    fun loadAvailabilityByCity(city: String) {
        viewModelScope.launch(Dispatchers.IO) {
            token = repository.getToken()
            availabilityInfoList.postValue(repository.getAvailabilityByCity(token, city))
        }
    }
}