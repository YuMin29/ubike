package com.yumin.ubike.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yumin.ubike.MapViewModel
import com.yumin.ubike.repository.UbikeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
class ViewModelModule {

    @Provides
    fun provideViewModelFactory(application: Application, repository: UbikeRepository):
            ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MapViewModel::class.java))
                    return MapViewModel(application, repository) as T

                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}