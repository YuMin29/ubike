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
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Response

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

    var sessionManager = SessionManager(getApplication())

    init {
        Log.d(TAG,"[init_block]")
        getToken()
    }

    private fun getToken() {
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch(Dispatchers.IO) {
                val response = repository.getToken()
                if (response.isSuccessful) {
                    response.body()?.let { result ->
                        var bodyContent = result.string()
                        var jsonObject = JSONObject(bodyContent)
                        Log.d(TAG, "getToken onResponse access_token = " + jsonObject.get("access_token"))
                        val token = jsonObject.get("access_token").toString()
                        sessionManager.saveAuthToken(String.format("Bearer %s", token))
                    }
                }
            }
        }
    }

    fun getAllCityStationInfo() {
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch {
                repeat(3) {
                }
//            progress.postValue(Event(true))
                val stationInfo = allCities.map { city ->
                    async {
                        var result = StationInfo()
                        val response = repository.getStationInfoByCity(city)
                        if (response.isSuccessful){
                            response.body()?.let {
                                result = it
                            }
                        }
                        result
                    }
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
                    async {
                        var result = AvailabilityInfo()
                        val response = repository.getAvailabilityByCity(city)
                        if (response.isSuccessful){
                            response.body()?.let {
                                result = it
                            }
                        }
                        result
                    }
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

                val response = repository.getStationInfoNearBy("nearby($latitude, $longitude, $distance)", queryServiceType)
                if (response.isSuccessful) {
                    response.body()?.let {
                        stationInfo.postValue(it)
                    }
                }
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
                    val response = repository.getAvailabilityInfoNearBy(
                        "nearby($latitude, $longitude, $distance)",
                        queryServiceType
                    )

                    if (response.isSuccessful) {
                        response.body()?.let {
                            availabilityInfo.postValue(it)
                        }
                    }
                } else {
                    val response = repository.getAvailabilityInfoNearBy(
                        "nearby($latitude, $longitude, $distance)",
                        queryServiceType
                    )

                    if (response.isSuccessful) {
                        response.body()?.let {
                            refreshAvailability.postValue(it)
                        }
                    }
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