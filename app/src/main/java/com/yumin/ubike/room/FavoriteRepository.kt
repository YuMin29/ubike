package com.yumin.ubike.room

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor (private val favoriteStationDao: FavoriteStationDao) {

    fun getAll() = favoriteStationDao.getAll()

    fun addToFavoriteList(favoriteStation: FavoriteStation) = favoriteStationDao.insert(favoriteStation)

    fun deleteFromFavoriteList(favoriteStation: FavoriteStation) = favoriteStationDao.delete(favoriteStation.uid)

    companion object{
        private var instance: FavoriteRepository? = null

        fun getInstance(favoriteStationDao: FavoriteStationDao) = instance ?: synchronized(this) {
            instance ?: FavoriteRepository(favoriteStationDao).also { instance = it }
        }
    }
}