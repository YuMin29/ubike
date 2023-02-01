package com.yumin.ubike

import android.content.Context
import android.util.Log
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
    private var token: String = ""
    var stationInfoByCity = MutableLiveData<StationInfo>()
    var availabilityInfoByCity = MutableLiveData<AvailabilityInfo>()

    var stationInfo = MutableLiveData<StationInfo>()
    var stationInfoTypeAll = MutableLiveData<StationInfo>()
    var stationInfoType1 = MutableLiveData<StationInfo>()
    var stationInfoType2 = MutableLiveData<StationInfo>()

    var availabilityInfo = MutableLiveData<AvailabilityInfo>()
    var availabilityInfoTypeAll = MutableLiveData<AvailabilityInfo>()
    var availabilityInfoType1 = MutableLiveData<AvailabilityInfo>()
    var availabilityInfoType2 = MutableLiveData<AvailabilityInfo>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            token = repository.getToken()
        }
    }

    fun getStationInfo(city: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (checkToken())
                stationInfoByCity.postValue(repository.getStationInfoByCity(token, city))
        }
    }

    fun getAvailabilityByCity(city: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (checkToken())
                availabilityInfoByCity.postValue(repository.getAvailabilityByCity(token, city))
        }
    }

    fun getStationInfoNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (checkToken()) {
                stationInfo.postValue(
                    repository.getStationInfoNearBy(
                        token,
                        "nearby($latitude, $longitude, $distance)"
                    )
                )
                getUbikeInfoByType(type)
            }
        }
    }

    fun getAvailabilityNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (checkToken()) {
                availabilityInfo.postValue(
                    repository.getAvailabilityInfoNearBy(
                        token,
                        "nearby($latitude, $longitude, $distance)"
                    )
                )
                getUbikeAvailabilityByType(type)
            }
        }
    }

    private fun checkToken(): Boolean {
        var result = false
        if (token == "") {
            viewModelScope.launch(Dispatchers.IO) {
                token = repository.getToken()
                result = true
            }
        } else {
            result = true
        }
        return result
    }

    fun getUbikeInfoByType(type: Int) {
        // 從這邊過濾不同類型的ubike
        val filterStationInfo = StationInfo()

        stationInfo.value?.iterator()?.forEach { infoItem ->
            Log.d(TAG, "[getUbikeInfoByType] service type = " + infoItem.serviceType)

            if (type != 0 && type != infoItem.serviceType) {
                Log.d(TAG, "[getUbikeInfoByType] RETURN")
                return@forEach
            }
            filterStationInfo.add(infoItem)
        }

        // post new station info
        when (type) {
            0 -> stationInfoTypeAll.postValue(filterStationInfo)
            1 -> stationInfoType1.postValue(filterStationInfo)
            2 -> stationInfoType2.postValue(filterStationInfo)
        }
    }

    fun getUbikeAvailabilityByType(type: Int){
        val filterAvailabilityInfo = AvailabilityInfo()

        availabilityInfo.value?.iterator()?.forEach { availabilityInfoItem ->
            Log.d(TAG, "[getUbikeAvailabilityByType] service type = " + availabilityInfoItem.ServiceType)

            if (type != 0 && type != availabilityInfoItem.ServiceType) {
                Log.d(TAG, "[getUbikeAvailabilityByType] RETURN")
                return@forEach
            }
            filterAvailabilityInfo.add(availabilityInfoItem)
        }

        when(type) {
            0 -> availabilityInfoTypeAll.postValue(filterAvailabilityInfo)
            1 -> availabilityInfoType1.postValue(filterAvailabilityInfo)
            2 -> availabilityInfoType2.postValue(filterAvailabilityInfo)
        }
    }

    companion object {
        const val TAG = "[MapViewModel]"
    }
}