package com.yumin.ubike

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.repository.RemoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapViewModel(private val repository: RemoteRepository, private val context: Context) : ViewModel() {
    lateinit var token : String
    var stationInfoList = MutableLiveData<StationInfo>()

    init {
        loadStationInfo()
    }

    private fun loadStationInfo(){
        viewModelScope.launch(Dispatchers.IO){
            token = repository.getToken()
            stationInfoList.postValue(repository.getStationInfoByCity(token,"NewTaipei"))
        }
    }
}