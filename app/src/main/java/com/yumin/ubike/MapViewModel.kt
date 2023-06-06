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
import java.io.IOException

class MapViewModel(private val repository: RemoteRepository, application: Application) : AndroidViewModel(application) {
    private val TAG = "[MapViewModel]"
    private var sessionManager = SessionManager(getApplication())
    private val allCities = arrayListOf(
        "Taichung", "Hsinchu", "MiaoliCounty", "NewTaipei", "PingtungCounty",
        "KinmenCounty", "Taoyuan", "Taipei", "Kaohsiung", "Tainan", "Chiayi", "HsinchuCounty"
    )

    var selectStationUid = MutableLiveData<Event<String>>()
    var searchStationUid = MutableLiveData<Event<Pair<StationInfoItem, AvailabilityInfoItem>>>()
    var tokenStatus = MutableLiveData<Resource<String>>()
    var stationInfo = MutableLiveData<Resource<StationInfo>>()
    var availabilityInfo = MutableLiveData<Resource<AvailabilityInfo>>()
    var refreshAvailability = MutableLiveData<Resource<AvailabilityInfo>>()
    var allCityStationInfo = MutableLiveData<Event<Resource<List<StationInfo>>>>()
    var allCityAvailabilityInfo = MutableLiveData<Event<Resource<List<AvailabilityInfo>>>>()


    init {
        Log.d(TAG, "[init_block]")
        getToken()
    }

    private fun getToken() {
        tokenStatus.postValue(Resource.Loading())
        try {
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
                        tokenStatus.postValue(Resource.Error(response.message()))
                    }
                }
            }
        } catch (t: Throwable) {
            tokenStatus.postValue(Resource.Error("exception:" + t.message))
        }
    }

    fun getAllCityStationInfo() {
        allCityStationInfo.postValue(Event(Resource.Loading()))

        try {
            if (NetworkChecker.checkConnectivity(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val transform1: (String) -> Deferred<StationInfo> = { cityName ->
                        async {
                            var result = StationInfo()
                            val response = repository.getStationInfoByCity(cityName)
                            if (response.isSuccessful) {
                                response.body()?.let {
                                    result = it
                                }
                            } else {
                                allCityStationInfo.postValue(Event(Resource.Error(response.message())))
                            }
                            result
                        }
                    }
                    val stationInfo = allCities.map(transform1).awaitAll()

                    Log.d(TAG, "[getAllCityStationInfo] [postValue]");
                    allCityStationInfo.postValue(Event(Resource.Success(stationInfo)))
                }
            }
        } catch (t: Throwable) {
            allCityStationInfo.postValue(Event(Resource.Error("exception:" + t.message)))
        }
    }

    fun getAllCityAvailabilityInfo() {
        allCityAvailabilityInfo.postValue(Event(Resource.Loading()))

        try {
            if (NetworkChecker.checkConnectivity(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val transform: (String) -> Deferred<AvailabilityInfo> = { cityName ->
                        async {
                            var result = AvailabilityInfo()
                            val response = repository.getAvailabilityByCity(cityName)
                            if (response.isSuccessful) {
                                response.body()?.let {
                                    result = it
                                }
                            } else {
                                Log.d(TAG, "[getAllCityAvailabilityInfo] fail type, " + response.code())
                                allCityAvailabilityInfo.postValue(Event(Resource.Error(response.message())))
                            }
                            result
                        }
                    }
                    val availabilityInfo = allCities.map(transform).awaitAll()
                    Log.d(TAG, "[getAllCityAvailabilityInfo] [postValue]");
                    allCityAvailabilityInfo.postValue(Event(Resource.Success(availabilityInfo)))
                }
            }
        } catch (t: Throwable) {
            allCityAvailabilityInfo.postValue(Event(Resource.Error("exception:" + t.message)))
        }
    }

    fun getStationInfoNearBy(latitude: Double, longitude: Double, distance: Int, type: Int) {
        Log.d(TAG, "[getStationInfoNearBy] latitude = $latitude , longitude = $longitude ,type = $type")
        stationInfo.postValue(Resource.Loading())

        try {
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
                            stationInfo.postValue(Resource.Success(it))
                        }
                    } else {
                        stationInfo.postValue(Resource.Error(response.message()))
                    }
                }
            }
        } catch (t: Throwable) {
            stationInfo.postValue(Resource.Error("exception:" + t.message))
        }
    }

    fun getAvailabilityNearBy(latitude: Double, longitude: Double, distance: Int, type: Int, refresh: Boolean) {
        if (!refresh)
            availabilityInfo.postValue(Resource.Loading())
        else
            refreshAvailability.postValue(Resource.Loading())

        Log.d(TAG, "[getAvailabilityNearBy] latitude = $latitude , longitude = $longitude")
        try {
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
                                availabilityInfo.postValue(Resource.Success(it))
                            else
                                refreshAvailability.postValue(Resource.Success(it))
                        }
                    } else {
                        if (!refresh)
                            availabilityInfo.postValue(Resource.Error(response.message()))
                        else
                            refreshAvailability.postValue(Resource.Error(response.message()))
                    }
                }
            }
        } catch (t: Throwable) {
            if (!refresh)
                availabilityInfo.postValue(Resource.Error("exception:" + t.message))
            else
                refreshAvailability.postValue(Resource.Error("exception:" + t.message))
        }
    }

    fun setSelectStationUid(uid: String) {
        selectStationUid.postValue(Event(uid))
    }

    fun setSelectSearchStationUid(stationInfoItem: StationInfoItem, availabilityInfoItem: AvailabilityInfoItem) {
        searchStationUid.postValue(Event(Pair(stationInfoItem, availabilityInfoItem)))
    }
}