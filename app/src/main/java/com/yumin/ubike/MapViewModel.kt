package com.yumin.ubike

import android.content.Context
import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.repository.RemoteRepository
import kotlinx.coroutines.*

class MapViewModel(private val repository: RemoteRepository) : ViewModel() {
    val allCities = arrayListOf<String>("Taichung","Hsinchu","MiaoliCounty","NewTaipei","PingtungCounty",
        "KinmenCounty","Taoyuan","Taipei","Kaohsiung","Tainan","Chiayi","HsinchuCounty")

    var selectStationUid = MutableLiveData<String>()

    var stationInfoByCity = MutableLiveData<StationInfo>()
    var availabilityInfoByCity = MutableLiveData<AvailabilityInfo>()

    var stationInfo = MutableLiveData<StationInfo>()

    var availabilityInfo = MutableLiveData<AvailabilityInfo>()

    var refreshAvailability = MutableLiveData<AvailabilityInfo>()

    var stationWholeInfo: MediatorLiveData<Pair<StationInfo?, AvailabilityInfo?>> =
        MediatorLiveData<Pair<StationInfo?, AvailabilityInfo?>>().apply {
            addSource(stationInfo) {
                value = Pair(it, availabilityInfo.value)
            }
            addSource(availabilityInfo) {
                value = Pair(stationInfo.value, it)
            }
        }

    var allCityStationInfo = MutableLiveData<List<StationInfo>>()
    var allCityAvailabilityInfo = MutableLiveData<List<AvailabilityInfo>>()

    var allInfo:MediatorLiveData<Pair<List<StationInfo>?,List<AvailabilityInfo>?>> =
        MediatorLiveData<Pair<List<StationInfo>?,List<AvailabilityInfo>?>>().apply {
            addSource(allCityStationInfo){
                value = Pair(it, allCityAvailabilityInfo.value)
            }
            addSource(allCityAvailabilityInfo){
                value = Pair(allCityStationInfo.value,it)
            }
    }

    companion object {
        const val TAG = "[MapViewModel]"
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getToken()
        }

//        getAllCityStationInfo()
//        getAllCityAvailabilityInfo()

        // TODO 20230208 要做一分鐘自動更新一次的功能
        // 要把最新一次座標&距離儲存起來，來當作最近一次更新的數據
        // 利用 CountdownTimer
        // 20230215 => finished

        // TODO 20230208 為搜尋頁面，撈全台縣市的資料回來
        // 也需要每分鐘更新一次資料
        // 利用 CountdownTimer

        // TODO 20230208 列表顯示，另開一個Activity
        // 把當前有的資料都線顯示在card view中
        // 再根據使用者滑到底部來決定增加搜尋的距離顯示
        // 跟地圖模式的live data分開
        // 也需要每分鐘更新一次資料(?)
    }

    fun getAllCityStationInfo() {
        viewModelScope.launch {
            val stationInfo = allCities.map { city ->
                async { repository.getStationInfoByCity(city) }
            }.awaitAll()
            allCityStationInfo.postValue(stationInfo)
        }
    }

    fun getAllCityAvailabilityInfo() {
        viewModelScope.launch {
            val availabilityInfo = allCities.map { city ->
                async { repository.getAvailabilityByCity(city) }
            }.awaitAll()
            allCityAvailabilityInfo.postValue(availabilityInfo)
        }
    }

    fun getStationInfo(city: String) {
        viewModelScope.launch(Dispatchers.IO) {
            stationInfoByCity.postValue(repository.getStationInfoByCity(city))
        }
    }

    fun getAvailabilityByCity(city: String) {
        viewModelScope.launch(Dispatchers.IO) {
            availabilityInfoByCity.postValue(repository.getAvailabilityByCity(city))
        }
    }

    fun getStationInfoNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        viewModelScope.launch(Dispatchers.IO) {

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

    fun getAvailabilityNearBy(
        latitude: Double,
        longitude: Double,
        distance: Int,
        type: Int,
        refresh: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {

            var queryServiceType: String? = when (type) {
                1 -> "ServiceType eq '1'"
                2 -> "ServiceType eq '2'"
                else -> null
            }

            if (!refresh) {
                availabilityInfo.postValue(
                    repository.getAvailabilityInfoNearBy(
                        "nearby($latitude, $longitude, $distance)",
                        queryServiceType
                    )
                )
            } else {
                refreshAvailability.postValue(
                    repository.getAvailabilityInfoNearBy(
                        "nearby($latitude, $longitude, $distance)",
                        queryServiceType
                    )
                )
            }
        }
    }

    fun setSelectStationUid(uid:String){
        selectStationUid.postValue(uid)
    }
}