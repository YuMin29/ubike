package com.yumin.ubike.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteStation::class], version = 1)
abstract class Database : RoomDatabase() {
    abstract fun favoriteStationDao(): FavoriteStationDao
}