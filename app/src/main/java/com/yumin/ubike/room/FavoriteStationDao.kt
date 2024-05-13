package com.yumin.ubike.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteStationDao {
    @Query("SELECT * FROM favoriteStation")
    fun getAll(): Flow<List<FavoriteStation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(favoriteStation: FavoriteStation)

    @Query("DELETE FROM favoriteStation WHERE uid = :uid")
    fun delete(uid: String)
}