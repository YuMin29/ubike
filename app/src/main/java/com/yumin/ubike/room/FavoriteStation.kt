package com.yumin.ubike.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FavoriteStation(
    @ColumnInfo(name = "uid") val uid: String,
    @PrimaryKey val id: Int? = null
)