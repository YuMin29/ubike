package com.yumin.ubike

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.repository.RemoteRepository
import kotlinx.coroutines.*

class MapViewModel(private val repository: RemoteRepository, application:Application) : AndroidViewModel(application) {
    private val TAG = "[MapViewModel]"
    private val allCities = arrayListOf("Taichung","Hsinchu","MiaoliCounty","NewTaipei","PingtungCounty",
        "KinmenCounty","Taoyuan","Taipei","Kaohsiung","Tainan","Chiayi","HsinchuCounty")

//    var progress = MutableLiveData<Event<Boolean>>()
    var selectStationUid = MutableLiveData<Event<String>>()
    var searchStationUid = MutableLiveData<Event<Pair<StationInfoItem,AvailabilityInfoItem>>>()
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

    var allInfo:MediatorLiveData<Event<Pair<List<StationInfo>?,List<AvailabilityInfo>?>>> =
        MediatorLiveData<Event<Pair<List<StationInfo>?,List<AvailabilityInfo>?>>>().apply {
            addSource(allCityStationInfo){
                value = Event(Pair(it, allCityAvailabilityInfo.value))
            }
            addSource(allCityAvailabilityInfo){
                value = Event(Pair(allCityStationInfo.value,it))
            }
    }

    init {
        Log.d(TAG,"[init]")
        if (NetworkChecker.checkConnectivity(getApplication())){
            viewModelScope.launch(Dispatchers.IO) {
                repository.getToken()
            }
        }

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
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch {
//            progress.postValue(Event(true))
                val stationInfo = allCities.map { city ->
                    async { repository.getStationInfoByCity(city) }
                }.awaitAll()
                Log.d(TAG,"[getAllCityStationInfo] [postValue]");
                allCityStationInfo.postValue(stationInfo)
//            progress.postValue(Event(false))
            }
        }
    }

    fun getAllCityAvailabilityInfo() {
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch {
//            progress.postValue(Event(true))
                val availabilityInfo = allCities.map { city ->
                    async { repository.getAvailabilityByCity(city) }
                }.awaitAll()
                Log.d(TAG,"[getAllCityAvailabilityInfo] [postValue]");
                allCityAvailabilityInfo.postValue(availabilityInfo)
//            progress.postValue(Event(false))
            }
        }
    }

    fun getStationInfoNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        if (NetworkChecker.checkConnectivity(getApplication())) {
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
    }

    fun getAvailabilityNearBy(latitude: Double, longitude: Double, distance: Int, type: Int, refresh: Boolean) {
        if (NetworkChecker.checkConnectivity(getApplication())) {
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
    }

    fun setSelectStationUid(uid:String){
        selectStationUid.postValue(Event(uid))
    }

    fun setSelectSearchStationUid(stationInfoItem: StationInfoItem,availabilityInfoItem: AvailabilityInfoItem){
        searchStationUid.postValue(Event(Pair(stationInfoItem,availabilityInfoItem)))
    }
}