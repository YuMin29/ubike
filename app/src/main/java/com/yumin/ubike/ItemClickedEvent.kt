package com.yumin.ubike

import android.content.Intent
import com.yumin.ubike.data.UbikeStationWithFavorite

sealed class FavoriteItemClickEvent {
    class ItemClick(val ubikeStationWithFavorite: UbikeStationWithFavorite) :
        FavoriteItemClickEvent()

    class ShareClick(val intent: Intent) : FavoriteItemClickEvent()
    class NavigationClick(val intent: Intent) : FavoriteItemClickEvent()
    class FavoriteClick(val isFavorite: Boolean, val stationUid: String) : FavoriteItemClickEvent()
}

interface FavoriteItemClickListener {
    fun onClick(favoriteClick: FavoriteItemClickEvent)
}