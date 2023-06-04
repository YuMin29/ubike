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

    var selectStationUid = MutableLiveData<Event<String>>()
    var searchStationUid = MutableLiveData<Event<Pair<StationInfoItem,AvailabilityInfoItem>>>()
    var stationInfo = MutableLiveData<StationInfo>()
    var availabilityInfo = MutableLiveData<AvailabilityInfo>()
    var refreshAvailability = MutableLiveData<AvailabilityInfo>()
    var allCityStationInfo = MutableLiveData<Event<List<StationInfo>>>()
    var allCityAvailabilityInfo = MutableLiveData<Event<List<AvailabilityInfo>>>()
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
                } else {
                    Log.d(TAG,"[getToken] fail type, "+response.code())
                }
            }
        }
    }

    fun getAllCityStationInfo() {
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch(Dispatchers.IO) {
                val transform1 : (String) -> Deferred<StationInfo> = { cityName ->
                    async {
                        var result = StationInfo()
                        val response = repository.getStationInfoByCity(cityName)
                        if (response.isSuccessful){
                            response.body()?.let {
                                result = it
                            }
                        } else {
                            Log.d(TAG,"[getAllCityStationInfo] fail type, "+response.code())
                        }
                        result
                    }
                }
                val stationInfo =  allCities.map(transform1).awaitAll()

                Log.d(TAG,"[getAllCityStationInfo] [postValue]");
                allCityStationInfo.postValue(Event(stationInfo))
            }
        }
    }

    fun getAllCityAvailabilityInfo() {
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch(Dispatchers.IO) {
                val transform : (String) -> Deferred<AvailabilityInfo> = { cityName ->
                    async {
                        var result = AvailabilityInfo()
                        val response = repository.getAvailabilityByCity(cityName)
                        if (response.isSuccessful){
                            response.body()?.let {
                                result = it
                            }
                        } else {
                            Log.d(TAG,"[getAllCityAvailabilityInfo] fail type, "+response.code())
                        }
                        result
                    }
                }
                val availabilityInfo = allCities.map(transform).awaitAll()
                Log.d(TAG,"[getAllCityAvailabilityInfo] [postValue]");
                allCityAvailabilityInfo.postValue(Event(availabilityInfo))
            }
        }
    }

    fun getStationInfoNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        Log.d(TAG,"[getStationInfoNearBy] latitude = $latitude , longitude = $longitude ,type = $type")
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
                } else {
                    Log.d(TAG,"[getStationInfoNearBy] fail type, "+response.code())
                }
            }
        }
    }

    fun getAvailabilityNearBy(latitude: Double, longitude: Double, distance: Int, type: Int, refresh: Boolean) {
        Log.d(TAG,"[getAvailabilityNearBy] latitude = $latitude , longitude = $longitude")
        if (NetworkChecker.checkConnectivity(getApplication())) {
            viewModelScope.launch(Dispatchers.IO) {

                var queryServiceType: String? = when (type) {
                    1 -> "ServiceType eq '1'"
                    2 -> "ServiceType eq '2'"
                    else -> null
                }

                val response = repository.getAvailabilityInfoNearBy(
                    "nearby($latitude, $longitude, $distance)",
                    queryServiceType
                )

                if (response.isSuccessful) {
                    response.body()?.let {
                        if (!refresh)
                            availabilityInfo.postValue(it)
                        else
                            refreshAvailability.postValue(it)
                    }
                } else {
                    Log.d(TAG,"[getAvailabilityNearBy] fail type, "+response.code())
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