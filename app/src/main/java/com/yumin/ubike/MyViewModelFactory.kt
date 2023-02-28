package com.yumin.ubike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yumin.ubike.repository.RemoteRepository

class MyViewModelFactory(private val remoteRepository: RemoteRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java))
            return MapViewModel(remoteRepository) as T

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}