package com.yumin.ubike

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.UbikeStationWithFavorite
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.UbikeStationInfoItem
import com.yumin.ubike.repository.UbikeRepository
import com.yumin.ubike.room.FavoriteRepository
import com.yumin.ubike.room.FavoriteStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject


@HiltViewModel
class MapViewModel @Inject constructor(
    application: Application,
    val repository: UbikeRepository,
    private val favoriteRepository: FavoriteRepository
) : AndroidViewModel(application) {
    private val TAG = "[MapViewModel]"
    private val cities = arrayListOf("Taipei", "NewTaipei", "Taoyuan", "Hsinchu", "HsinchuCounty",
        "MiaoliCounty", "Taichung", "Chiayi", "Tainan", "Kaohsiung", "PingtungCounty")

    private var jobNearbyStation: Job? = null
    private var jobFavoriteStation: Job? = null
    private var jobSearchStation: Job? = null

    private var _selectStationUid = MutableSharedFlow<String>()
    var selectStationUid: SharedFlow<String> = _selectStationUid

    private var _searchStationUid = MutableSharedFlow<UbikeStationWithFavorite>()
    var searchStationUid: SharedFlow<UbikeStationWithFavorite> = _searchStationUid

    private var _nearbyStation: MutableStateFlow<List<UbikeStationWithFavorite>> = MutableStateFlow(emptyList())
    var nearbyStation: StateFlow<List<UbikeStationWithFavorite>> = _nearbyStation

    private var _queryStationName: MutableStateFlow<String?> = MutableStateFlow(null)
    var queryStationName: StateFlow<String?> = _queryStationName

    private var nearby: MutableStateFlow<List<UbikeStationInfoItem>> = MutableStateFlow(emptyList())

    private var allCityStationInfo = MutableStateFlow(StationInfo())

    private var ubikeStationInfo = MutableStateFlow<List<UbikeStationInfoItem>>(emptyList())

    private val cityStationInfo: Flow<StationInfo> = flow {
        val result = StationInfo()
        cities.forEach { city ->
            delay(300) // need delay here because  the tdx restriction is call api 5 times per second
            val response = repository.getStationInfoByCity(city)
            if (response.isSuccessful) {
                Log.d(TAG, "[getAllCityStationInfo] cityName = $city, isSuccessful")
                response.body()?.let {
                    result.addAll(it)
                }
            } else {
                Log.d(TAG, "[getAllCityStationInfo] cityName = $city, fail code = ${response.code()}, ${response.message()}")
                // Handle error if needed
            }
        }
        emit(result)
    }

    private val cityAvailableInfo = flow {
        val result = AvailabilityInfo()
        cities.forEach { city ->
            delay(300) // need delay here because  the tdx restriction is call api 5 times per second

            val response = repository.getAvailabilityByCity(city)
            if (response.isSuccessful) {
                Log.d(TAG, "[getAvailabilityByCity] cityName = $city, isSuccessful")
                response.body()?.let {
                    result.addAll(it)
                }
            } else {
                Log.d(TAG, "[getAvailabilityByCity] cityName = $city, fail code = ${response.code()}, ${response.message()}")
                // Handle error if needed
            }
        }
        emit(result)
    }

    init {
        viewModelScope.launch {
            cityStationInfo.collect {
                allCityStationInfo.value = it
            }
        }
        getWholeCityStation()
    }

    private fun getWholeCityStation() {
        viewModelScope.launch {
            delay(2000)
            val bikeStationInfoList = mutableListOf<UbikeStationInfoItem>()
            combine(allCityStationInfo, cityAvailableInfo) { allStationInfo, allAvailabilityInfo ->
                allStationInfo.map { stationInfoItem ->
                    val availabilityInfo = allAvailabilityInfo.find { stationInfoItem.stationUID == it.StationUID }
                    availabilityInfo?.let {
                        bikeStationInfoList.add(
                            UbikeStationInfoItem(
                                it.AvailableRentBikes,
                                availabilityInfo.AvailableRentBikesDetail,
                                availabilityInfo.AvailableReturnBikes,
                                availabilityInfo.ServiceStatus,
                                availabilityInfo.ServiceType,
                                stationInfoItem.stationID,
                                stationInfoItem.authorityID,
                                stationInfoItem.bikesCapacity,
                                stationInfoItem.srcUpdateTime,
                                stationInfoItem.stationAddress,
                                stationInfoItem.stationName,
                                stationInfoItem.stationPosition,
                                stationInfoItem.stationUID,
                                stationInfoItem.updateTime
                            )
                        )
                    }
                }
                bikeStationInfoList
            }.collect {
                ubikeStationInfo.value = it
            }
        }
    }

    fun refreshAllCityStation() {
        getWholeCityStation()
    }

    fun addToFavoriteList(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "addToFavoriteList , UID => $uid")
            favoriteRepository.addToFavoriteList(FavoriteStation(uid))
        }
    }

    fun removeFromFavoriteList(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "removeFromFavoriteList , UID => $uid")
            favoriteRepository.deleteFromFavoriteList(FavoriteStation(uid))
        }
    }

    val favoriteStation: StateFlow<List<UbikeStationWithFavorite>> = ubikeStationInfo.combine(favoriteRepository.getAll()) { ubikeStationInfo, favoriteList ->
        ubikeStationInfo
            .filter { item ->
                favoriteList.any { it.uid == item.stationUID }
            }
            .map {
                UbikeStationWithFavorite(it, true)
            }
    }.stateIn(
        scope = viewModelScope,
        initialValue = emptyList(),
        started = SharingStarted.WhileSubscribed()
    )

    val searchStation = combine(_queryStationName, ubikeStationInfo, favoriteRepository.getAll()) { _queryStationName, wholeStationList, favoriteStationList ->
        if (!_queryStationName.isNullOrEmpty()) {
            wholeStationList
                .filter { stationItem ->
                    stationItem.stationName.zhTw.contains(_queryStationName!!)
                }
                .map { stationItem ->
                    val isFavorite =
                        favoriteStationList.any { it.uid == stationItem.stationUID }
                    UbikeStationWithFavorite(stationItem, isFavorite)
                }
        } else {
            Log.d(TAG, "[testSearchStation] emptyList")
            emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        initialValue = emptyList(),
        started = SharingStarted.WhileSubscribed()
    )

    fun queryStation(queryStationName: String?) {
        _queryStationName.update { queryStationName }
    }

    fun getNearbyStation(
        latitude: Double,
        longitude: Double,
        distance: Int,
        type: Int
    ) {
        jobNearbyStation?.cancel()
        if (NetworkChecker.checkConnectivity(getApplication())) {
            jobNearbyStation =
                viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                    collectNearbyStation(latitude, longitude, distance, type)

                    combine(
                        nearby,
                        favoriteRepository.getAll()
                    ) { nearbyStationInfo, favoriteStationList ->
                        nearbyStationInfo
                            .map { stationItem ->
                                val isFavorite =
                                    favoriteStationList.any { it.uid == stationItem.stationUID }
                                UbikeStationWithFavorite(stationItem, isFavorite)
                            }
                    }.collect {
                        _nearbyStation.value = it
                    }
            }
        }
    }

    private suspend fun collectNearbyStation(
        latitude: Double,
        longitude: Double,
        distance: Int,
        type: Int
    ) {
        var queryServiceType: String? = when (type) {
            1 -> "ServiceType eq '1'"
            2 -> "ServiceType eq '2'"
            else -> null
        }

        val stationInfoFlow = flow {
            val response =  repository.getStationInfoNearBy("nearby($latitude, $longitude, $distance)", queryServiceType)
            if (response.isSuccessful) {
                Log.d(TAG, "[stationInfoFlow] is success")
                emit(response.body())
            } else {
                Log.d(TAG, "[stationInfoFlow] fail code = ${response.code()}, ${response.message()}")
                // Handle error if needed
            }
        }

        val availabilityInfoFlow = flow{
            val response =  repository.getAvailabilityInfoNearBy("nearby($latitude, $longitude, $distance)", queryServiceType)
            if (response.isSuccessful) {
                Log.d(TAG, "[stationInfoFlow] is success")
                emit(response.body())
            } else {
                Log.d(TAG, "[stationInfoFlow] fail code = ${response.code()}, ${response.message()}")
                // Handle error if needed
            }
        }
        val nearbyStationList = mutableListOf<UbikeStationInfoItem>()
        combine(stationInfoFlow,availabilityInfoFlow) {
            stationInfo,availabilityInfo ->

            stationInfo?.map { stationItem ->
                val item = availabilityInfo?.find { it.StationUID == stationItem.stationUID }

                if (item != null) {
                    nearbyStationList.add(UbikeStationInfoItem(
                        item.AvailableRentBikes,
                        item.AvailableRentBikesDetail,
                        item.AvailableReturnBikes,
                        item.ServiceStatus,
                        item.ServiceType,
                        stationItem.stationID,
                        stationItem.authorityID,
                        stationItem.bikesCapacity,
                        stationItem.srcUpdateTime,
                        stationItem.stationAddress,
                        stationItem.stationName,
                        stationItem.stationPosition,
                        stationItem.stationUID,
                        stationItem.updateTime
                    ))
                }
            }
            nearbyStationList
        }.collect {
            nearby.value = it
        }
    }

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }

    fun setSelectStationUid(uid: String) {
        Log.d(TAG, "[selectStationUid.postValue] = $uid")
        viewModelScope.launch {
            _selectStationUid.emit(uid)
        }
    }

    fun setSearchStationUid(ubikeStationWithFavorite: UbikeStationWithFavorite) {
        viewModelScope.launch {
            _searchStationUid.emit(ubikeStationWithFavorite)
        }
    }

    override fun onCleared() {
        super.onCleared()
        jobNearbyStation?.cancel()
        jobFavoriteStation?.cancel()
        jobSearchStation?.cancel()
    }
}